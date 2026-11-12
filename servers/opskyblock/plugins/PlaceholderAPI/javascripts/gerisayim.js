var day = "gün";
var hour = "saat";
var minute = "dakika";
var second = "saniye";

function leapyear(year) {
  return year % 100 === 0 ? year % 400 === 0 : year % 4 === 0;
}

function getdays(date){
    date = new Date(date);
    var month = date.getMonth()+1;
    if(month == 1) return 31;
    if(month == 2 && leapyear(date.getYear())) return 29;
    if(month == 2 && !leapyear(date.getYear())) return 28;
    if(month == 3) return 31;
    if(month == 4) return 30;
    if(month == 5) return 31;
    if(month == 6) return 30;
    if(month == 7) return 31;
    if(month == 8) return 31;
    if(month == 9) return 30;
    if(month == 10) return 31;
    if(month == 11) return 30;
    if(month == 12) return 31;
}
function givestr(count,islv){
    if(count > 0 || true) return count+" "+islv+" ";
    else return "";
}
function kalan(ms){
    var days = Math.floor(ms / (24*60*60*1000));
    var daysms = ms % (24*60*60*1000);
    var hours = Math.floor((daysms)/(60*60*1000));
    var hoursms = ms % (60*60*1000);
    var minutes = Math.floor((hoursms)/(60*1000));
    var minutesms = ms % (60*1000);
    var seconds = Math.floor((minutesms)/(1000));
    return givestr(days,day)+givestr(hours,hour)+givestr(minutes,minute)+givestr(seconds,second);
}
function countdown() {
    var dt = new Date(Date.now());
    var edt = new Date(Date.now());
    edt.setDate(getdays(dt));
    edt.setHours(23);
    edt.setMinutes(59);
    edt.setSeconds(59);
    edt.setMilliseconds(999);
    return kalan(Date.parse(edt) - Date.parse(dt));
}
countdown();