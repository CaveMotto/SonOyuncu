using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Text.RegularExpressions;
using System.Windows.Forms;

// MottoSMP giris penceresi: herkes kendi adini yazar, ad bu Windows kullanicisina ozel saklanir.
class LoginForm : Form
{
    // Lacivert zemin: logonun beyaz sticker kenari ancak koyu zeminde okunur.
    static readonly Color Bg      = Color.FromArgb(14, 22, 38);
    static readonly Color FieldBg = Color.FromArgb(26, 38, 62);
    static readonly Color Ink     = Color.FromArgb(236, 241, 250);
    static readonly Color Muted   = Color.FromArgb(168, 182, 206);   // Bg uzerinde ~8:1
    // Tek vurgu rengi: cilegin mavisi, sadece ana buton ve odak cizgisinde.
    static readonly Color Blue    = Color.FromArgb(38, 132, 230);
    // Buton icin koyulastirilmis mavi: beyaz yaziyla 5.1:1 (AA).
    static readonly Color ButtonBlue  = Color.FromArgb(28, 110, 200);
    static readonly Color ButtonHover = Color.FromArgb(22, 96, 178);
    static readonly Color Error   = Color.FromArgb(255, 138, 128);

    readonly string gameDir;
    readonly string nameFile = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "MottoSMP", "isim.txt");

    TextBox nameBox;
    Panel underline;
    Label status;
    Button play;

    public LoginForm(string gameDir)
    {
        this.gameDir = gameDir;
        Text = "MottoSMP";
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(360, 440);
        BackColor = Bg;
        Font = new Font("Segoe UI", 10f);
        KeyPreview = true;
        KeyDown += (s, e) => { if (e.KeyCode == Keys.Escape) Close(); };
        try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }

        var logo = new PictureBox { Bounds = new Rectangle(115, 28, 130, 130), SizeMode = PictureBoxSizeMode.Zoom, AccessibleName = "MottoSMP logosu" };
        string lp = Path.Combine(gameDir, "logo.png");
        if (File.Exists(lp)) logo.Image = Image.FromFile(lp);
        Controls.Add(logo);

        // Logodaki iki tonu yaziya tasiyan kimlik motifi: "Motto" mavi, "SMP" beyaz.
        var title = new Panel { Bounds = new Rectangle(0, 164, 360, 44), BackColor = Bg, AccessibleName = "MottoSMP" };
        var titleFont = new Font("Segoe UI Black", 22f);
        title.Paint += (s, e) => {
            var f = TextFormatFlags.NoPadding | TextFormatFlags.VerticalCenter;
            int w1 = TextRenderer.MeasureText(e.Graphics, "Motto", titleFont, Size.Empty, f).Width;
            int w2 = TextRenderer.MeasureText(e.Graphics, "SMP", titleFont, Size.Empty, f).Width;
            int x = (title.Width - w1 - w2) / 2;
            TextRenderer.DrawText(e.Graphics, "Motto", titleFont, new Rectangle(x, 0, w1, title.Height), Blue, f);
            TextRenderer.DrawText(e.Graphics, "SMP", titleFont, new Rectangle(x + w1, 0, w2, title.Height), Ink, f);
        };
        Controls.Add(title);

        var label = new Label { Text = "Oyun içi adın", ForeColor = Muted, Bounds = new Rectangle(40, 232, 280, 22) };
        Controls.Add(label);

        nameBox = new TextBox {
            Bounds = new Rectangle(40, 258, 280, 30), BackColor = FieldBg, ForeColor = Ink,
            BorderStyle = BorderStyle.None, Font = new Font("Segoe UI", 13f), MaxLength = 16,
            AccessibleName = "Oyun içi adın" };
        var fieldWrap = new Panel { Bounds = new Rectangle(36, 252, 288, 36), BackColor = FieldBg };
        nameBox.Location = new Point(4, 5); nameBox.Width = 280;
        fieldWrap.Controls.Add(nameBox);
        fieldWrap.Click += (s, e) => nameBox.Focus();
        Controls.Add(fieldWrap);

        // Odak gostergesi: alan seciliyken mavi, degilken soluk cizgi.
        underline = new Panel { Bounds = new Rectangle(36, 288, 288, 2), BackColor = FieldBg };
        Controls.Add(underline);
        nameBox.GotFocus += (s, e) => underline.BackColor = Blue;
        nameBox.LostFocus += (s, e) => underline.BackColor = FieldBg;
        nameBox.TextChanged += (s, e) => { status.Text = ""; status.ForeColor = Muted; };

        status = new Label { ForeColor = Muted, Bounds = new Rectangle(40, 296, 280, 40), Font = new Font("Segoe UI", 9f),
            Text = "3-16 karakter: harf, rakam veya _" };
        Controls.Add(status);

        play = new Button {
            Text = "Oyuna gir", Bounds = new Rectangle(36, 342, 288, 46), FlatStyle = FlatStyle.Flat,
            BackColor = ButtonBlue, ForeColor = Color.White, Font = new Font("Segoe UI Semibold", 11.5f), Cursor = Cursors.Hand };
        play.FlatAppearance.BorderSize = 0;
        play.FlatAppearance.MouseOverBackColor = ButtonHover;
        play.FlatAppearance.MouseDownBackColor = Color.FromArgb(18, 82, 156);
        // Klavyeyle secildiginde gorunur odak cercevesi.
        play.GotFocus += (s, e) => { play.FlatAppearance.BorderSize = 2; play.FlatAppearance.BorderColor = Ink; };
        play.LostFocus += (s, e) => play.FlatAppearance.BorderSize = 0;
        play.Click += (s, e) => Launch();
        Controls.Add(play);
        AcceptButton = play;

        var hint = new Label {
            Text = "Şifreni oyunda yazarsın: ilk girişte /register, sonra /login",
            ForeColor = Muted, Font = new Font("Segoe UI", 8.5f), TextAlign = ContentAlignment.MiddleCenter,
            Bounds = new Rectangle(0, 400, 360, 22) };
        Controls.Add(hint);

        try { if (File.Exists(nameFile)) nameBox.Text = File.ReadAllText(nameFile).Trim(); } catch { }
        Shown += (s, e) => { nameBox.Focus(); nameBox.SelectAll(); };
    }

    void Launch()
    {
        string name = nameBox.Text.Trim();
        if (!Regex.IsMatch(name, "^[a-zA-Z0-9_]{3,16}$"))
        {
            status.ForeColor = Error;
            status.Text = name.Length == 0 ? "Adını yaz." : "Ad 3-16 karakter olmalı. Sadece harf, rakam ve _ kullan.";
            nameBox.Focus();
            return;
        }

        string bat = Path.Combine(gameDir, "start.bat");
        if (!File.Exists(bat))
        {
            status.ForeColor = Error;
            status.Text = "start.bat bulunamadı: " + gameDir;
            return;
        }

        try { Directory.CreateDirectory(Path.GetDirectoryName(nameFile)); File.WriteAllText(nameFile, name); } catch { }

        play.Enabled = false;
        play.Text = "Açılıyor...";
        var psi = new ProcessStartInfo {
            FileName = "cmd.exe", Arguments = "/c \"\"" + bat + "\"\"",
            WorkingDirectory = gameDir, UseShellExecute = false, CreateNoWindow = true };
        psi.EnvironmentVariables["MOTTO_USER"] = name;
        psi.EnvironmentVariables["MOTTO_LAUNCHER"] = "1";
        try { Process.Start(psi); Close(); }
        catch (Exception ex)
        {
            play.Enabled = true; play.Text = "Oyuna gir";
            status.ForeColor = Error; status.Text = "Başlatılamadı: " + ex.Message;
        }
    }
}

static class Program
{
    [STAThread]
    static void Main()
    {
        string dir = AppDomain.CurrentDomain.BaseDirectory;
        string game = Directory.Exists(Path.Combine(dir, "CraftRise")) ? Path.Combine(dir, "CraftRise") : dir;
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new LoginForm(game));
    }
}
