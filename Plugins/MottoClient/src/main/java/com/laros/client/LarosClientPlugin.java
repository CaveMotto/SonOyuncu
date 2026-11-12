package com.laros.client;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LarosClientPlugin extends JavaPlugin implements Listener {

    private static final String BRAND_CHANNEL_LEGACY = "MC|Brand";
    private static final String BRAND_CHANNEL = "minecraft:brand";

    private final Set<String> allowedBrands = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<UUID> verifiedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> awaitingBrand = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    private int timeoutSeconds;
    private String kickMessage;
    private boolean debug;

    private String hsKey;
    private String hsValue;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocalConfig();

        getServer().getMessenger().registerIncomingPluginChannel(this, BRAND_CHANNEL, (ch, player, message) -> handleBrand(player, message));
        getServer().getMessenger().registerOutgoingPluginChannel(this, BRAND_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, BRAND_CHANNEL_LEGACY, (ch, player, message) -> handleBrand(player, message));
        getServer().getMessenger().registerOutgoingPluginChannel(this, BRAND_CHANNEL_LEGACY);

        // Intercept handshake to block early
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        pm.addPacketListener(new PacketAdapter(this, PacketType.Handshake.Client.SET_PROTOCOL) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                try {
                    // Only enforce for LOGIN handshakes (state 2). STATUS (state 1) must pass for MOTD/icon.
                    int nextState = 2;
                    try {
                        if (event.getPacket().getIntegers().size() >= 2) {
                            nextState = event.getPacket().getIntegers().read(1);
                        }
                    } catch (Throwable ignored) {}
                    if (nextState != 2) {
                        return; // Allow STATUS pings unaffected
                    }
                    String host = event.getPacket().getStrings().read(0);
                    if (debug) getLogger().info("Handshake host: " + host);
                    if (!hostContainsToken(host)) {
                        event.setCancelled(true);
                        try { if (event.getPlayer() != null) event.getPlayer().kickPlayer(kickMessage); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        });

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        allowedBrands.clear();
        verifiedPlayers.clear();
        awaitingBrand.clear();
    }

    private void reloadLocalConfig() {
        reloadConfig();
        FileConfiguration cfg = getConfig();
        allowedBrands.clear();
        allowedBrands.addAll(new HashSet<String>(cfg.getStringList("allowed-brands")));
        timeoutSeconds = Math.max(1, cfg.getInt("verification-timeout-seconds", 5));
        kickMessage = color(cfg.getString("kick-message", "&cBu sunucuya sadece &bLarosClient &cile girebilirsiniz!"));
        debug = cfg.getBoolean("debug", false);
        hsKey = cfg.getString("handshake-token-key", "LAROS-TOKEN");
        hsValue = cfg.getString("handshake-token-value", "LC-ONLY");
    }

    private boolean hostContainsToken(String host) {
        if (hsKey == null || hsKey.isEmpty()) return true;
        if (host == null) return false;
        String lower = host.toLowerCase();
        return lower.contains((hsKey + "=" + hsValue).toLowerCase());
    }

    private static final String TRIM_HANDLER = "motto_useentity_trim";

    private void injectUseEntityTrim(Player player) {
        try {
            io.netty.channel.Channel ch = ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) player)
                    .getHandle().playerConnection.networkManager.channel;
            if (ch.pipeline().get(TRIM_HANDLER) != null) return;
            ch.pipeline().addBefore("decoder", TRIM_HANDLER, new UseEntityTrimHandler());
        } catch (Throwable t) {
            getLogger().warning("UseEntity filtresi eklenemedi: " + t);
        }
    }

    // CraftRise client appends extra bytes to PacketPlayInUseEntity; vanilla 1.8 decoder rejects them.
    private static final class UseEntityTrimHandler extends io.netty.channel.ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof io.netty.buffer.ByteBuf) {
                io.netty.buffer.ByteBuf buf = (io.netty.buffer.ByteBuf) msg;
                int start = buf.readerIndex();
                try {
                    if (readVarInt(buf) == 0x02) {
                        readVarInt(buf);
                        int type = readVarInt(buf);
                        if (type == 2) buf.skipBytes(12);
                        int end = buf.readerIndex();
                        if (buf.writerIndex() > end) buf.writerIndex(end);
                    }
                } catch (Throwable ignored) {
                } finally {
                    buf.readerIndex(start);
                }
            }
            super.channelRead(ctx, msg);
        }

        private static int readVarInt(io.netty.buffer.ByteBuf buf) {
            int result = 0, n = 0;
            byte b;
            do {
                b = buf.readByte();
                result |= (b & 0x7F) << (7 * n++);
                if (n > 5) throw new RuntimeException("VarInt too big");
            } while ((b & 0x80) != 0);
            return result;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        injectUseEntityTrim(player);
        awaitingBrand.add(player.getUniqueId());
        verifiedPlayers.remove(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            UUID id = player.getUniqueId();
            if (awaitingBrand.contains(id) && !verifiedPlayers.contains(id)) {
                player.kickPlayer(kickMessage);
            }
        }, timeoutSeconds * 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        awaitingBrand.remove(id);
        verifiedPlayers.remove(id);
    }

    private void handleBrand(Player player, byte[] message) {
        String brand = readBrand(message);
        UUID id = player.getUniqueId();
        if (!awaitingBrand.contains(id)) return;
        if (isAllowedBrand(brand)) {
            verifiedPlayers.add(id);
            awaitingBrand.remove(id);
        } else {
            player.kickPlayer(kickMessage);
        }
    }

    private boolean isAllowedBrand(String brand) {
        if (brand == null) return false;
        String b = brand.trim().toLowerCase();
        if (b.isEmpty()) return false;
        for (String allowed : allowedBrands) {
            if (b.contains(allowed.toLowerCase())) return true;
        }
        getLogger().info("Reddedilen brand: '" + brand + "'");
        return false;
    }

    private String readBrand(byte[] message) {
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            int len = readVarInt(in);
            byte[] bytes = new byte[len];
            in.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
        return new String(message, StandardCharsets.UTF_8);
    }

    private int readVarInt(ByteArrayDataInput in) {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = (read & 0b01111111);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new RuntimeException("VarInt is too big");
        } while ((read & 0b10000000) != 0);
        return result;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("larosclient.admin")) {
                sender.sendMessage(ChatColor.RED + "Yetkin yok.");
                return true;
            }
            reloadLocalConfig();
            sender.sendMessage(ChatColor.GREEN + "Konfig yeniden yüklendi.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("larosclient.admin")) {
                sender.sendMessage(ChatColor.RED + "Yetkin yok.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Kullanım: /" + label + " give <oyuncu> <id>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Oyuncu çevrimiçi değil: " + args[1]);
                return true;
            }
            int id;
            try { id = Integer.parseInt(args[2]); } catch (Exception ex) {
                sender.sendMessage(ChatColor.RED + "Geçersiz id.");
                return true;
            }
            ItemStack stack;
            String display;
            if (id == 5000) { stack = new ItemStack(Material.DIAMOND_HELMET); display = ChatColor.AQUA + "Titanium Miğfer"; }
            else if (id == 5001) { stack = new ItemStack(Material.DIAMOND_CHESTPLATE); display = ChatColor.AQUA + "Titanium Göğüslük"; }
            else if (id == 5002) { stack = new ItemStack(Material.DIAMOND_LEGGINGS); display = ChatColor.AQUA + "Titanium Pantolon"; }
            else if (id == 5003) { stack = new ItemStack(Material.DIAMOND_BOOTS); display = ChatColor.AQUA + "Titanium Bot"; }
            else { stack = new ItemStack(Material.DIAMOND_SWORD); display = ChatColor.AQUA + "Titanium Kılıç"; }

            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(display);
                stack.setItemMeta(meta);
            }
            target.getInventory().addItem(stack);
            sender.sendMessage(ChatColor.GREEN + target.getName() + " adlı oyuncuya verdi: " + display + ChatColor.GRAY + " (#" + id + ")");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "/" + label + " reload | give <oyuncu> <id>");
        return true;
    }
}


