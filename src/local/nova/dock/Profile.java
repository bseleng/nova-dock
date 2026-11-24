package local.nova.dock;

/** Immutable preset values. The fan combinations mirror Retroid's quick settings UI. */
final class Profile {
    static final int[] RATES = {0,60,120};
    static final int RATE=0, PERFORMANCE=1, FAN=2, BYPASS=3, CONTROLLER=4;
    final boolean docked;
    final boolean bypassCharging;
    final int rate, fan, performance, layout;
    Profile(boolean docked, int rate, int fan, int performance, int layout, boolean bypassCharging) {
        if (performance<0 || performance>2) throw new IllegalArgumentException("Неизвестный режим производительности");
        if (rate!=0 && rate!=60 && rate!=120) throw new IllegalArgumentException("Неизвестная частота");
        boolean validFan=false;
        for (int f : fans(performance)) if (f==fan) validFan=true;
        if (!validFan) throw new IllegalArgumentException("Режим вентилятора несовместим с производительностью");
        if (!docked && layout!=0 && layout!=1) throw new IllegalArgumentException("Неизвестная раскладка");
        this.docked=docked; this.rate=rate; this.fan=fan; this.performance=performance; this.layout=docked?2:layout;
        this.bypassCharging=bypassCharging;
    }
    static Profile defaults(boolean docked) { return new Profile(docked,docked?60:120,docked?6:1,docked?2:0,0,docked); }
    static int[] fans(int performance) {
        return performance==2 ? new int[]{4,5,6} : performance==1 ? new int[]{1,4,5,6} : new int[]{0,1,4,5,6};
    }
    static String fanLabel(int value) {
        switch(value) { case 0:return "Выключен"; case 1:return "Quiet"; case 4:return "Smart"; case 5:return "Sport"; case 6:return "Максимум"; default:return "Неизвестно"; }
    }
    static String performanceLabel(int value) { return value==2?"High Performance":value==1?"Performance":"Standard"; }
    static String rateLabel(int rate) { return rate==0?"Не менять":rate+" Гц"; }
    Profile next(int setting) {
        return next(setting,intValue(setting),performance);
    }
    Profile next(int setting,int current,int currentPerformance) {
        int r=rate, p=performance, f=fan, l=layout;
        boolean b=bypassCharging;
        switch (setting) {
            case RATE: r=current==0?60:current==60?120:0; break;
            case PERFORMANCE:
                p=(current+1)%3;
                boolean allowed=false;
                for (int option:fans(p)) if (option==f) allowed=true;
                if (!allowed) f=4;
                break;
            case FAN:
                int[] options=fans(performance);
                int start=-1;
                for (int i=0;i<options.length;i++) if (options[i]==current) start=i;
                for (int step=1;step<=options.length;step++) {
                    int candidate=options[(start+step)%options.length];
                    if (fanAllowed(currentPerformance,candidate)) { f=candidate; break; }
                }
                break;
            case BYPASS: b=current==0; break;
            case CONTROLLER: if (docked) return this; l=current==0?1:0; break;
            default: throw new IllegalArgumentException("Неизвестная настройка");
        }
        return new Profile(docked,r,f,p,l,b);
    }
    static boolean fanAllowed(int performance,int fan) {
        for (int candidate:fans(performance)) if (candidate==fan) return true;
        return false;
    }
    int intValue(int setting) {
        switch (setting) {
            case RATE:return rate;
            case PERFORMANCE:return performance;
            case FAN:return fan;
            case BYPASS:return bypassCharging?1:0;
            case CONTROLLER:return layout;
            default:throw new IllegalArgumentException("Неизвестная настройка");
        }
    }
    static String settingLabel(int setting) {
        switch (setting) {
            case RATE:return "Частота";
            case PERFORMANCE:return "Производительность";
            case FAN:return "Вентилятор";
            case BYPASS:return "Обходная зарядка";
            case CONTROLLER:return "Контроллер";
            default:throw new IllegalArgumentException("Неизвестная настройка");
        }
    }
    String value(int setting) {
        return valueLabel(setting,intValue(setting));
    }
    static String valueLabel(int setting,int value) {
        switch (setting) {
            case RATE:return rateLabel(value);
            case PERFORMANCE:return performanceLabel(value);
            case FAN:return fanLabel(value);
            case BYPASS:return value==1?"Включена":"Выключена";
            case CONTROLLER:return value==2?"Выключен":value==0?"Xbox":"Retro";
            default:throw new IllegalArgumentException("Неизвестная настройка");
        }
    }
    String body() { return (docked?"Контроллер выключен":"Контроллер "+(layout==0?"Xbox":"Retro"))+"\nВентилятор: "+fanLabel(fan)+"\n"+performanceLabel(performance)+"\nОбходная зарядка: "+(bypassCharging?"вкл.":"выкл."); }
    String status() { return (docked?"В доке":"Портативный режим")+" · "+(rate==0?"частота без изменения":rateLabel(rate))+" · "+fanLabel(fan); }
}
