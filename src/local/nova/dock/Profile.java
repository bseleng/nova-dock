package local.nova.dock;

/** Immutable preset values. The fan combinations mirror Retroid's quick settings UI. */
final class Profile {
    static final int[] RATES = {0,60,120};
    final boolean docked;
    final int rate, fan, performance, layout;
    Profile(boolean docked, int rate, int fan, int performance, int layout) {
        if (performance<0 || performance>2) throw new IllegalArgumentException("Неизвестный режим производительности");
        if (rate!=0 && rate!=60 && rate!=120) throw new IllegalArgumentException("Неизвестная частота");
        boolean validFan=false;
        for (int f : fans(performance)) if (f==fan) validFan=true;
        if (!validFan) throw new IllegalArgumentException("Режим вентилятора несовместим с производительностью");
        if (!docked && layout!=0 && layout!=1) throw new IllegalArgumentException("Неизвестная раскладка");
        this.docked=docked; this.rate=rate; this.fan=fan; this.performance=performance; this.layout=docked?2:layout;
    }
    static Profile defaults(boolean docked) { return new Profile(docked,docked?60:120,docked?6:1,docked?2:0,0); }
    static int[] fans(int performance) {
        return performance==2 ? new int[]{4,5,6} : performance==1 ? new int[]{1,4,5,6} : new int[]{0,1,4,5,6};
    }
    static String fanLabel(int value) {
        switch(value) { case 0:return "Выключен"; case 1:return "Quiet"; case 4:return "Smart"; case 5:return "Sport"; case 6:return "Максимум"; default:return "Неизвестно"; }
    }
    static String performanceLabel(int value) { return value==2?"High Performance":value==1?"Performance":"Standard"; }
    static String rateLabel(int rate) { return rate==0?"Не менять":rate+" Гц"; }
    String body() { return (docked?"Контроллер выключен":"Контроллер "+(layout==0?"Xbox":"Retro"))+"\nВентилятор: "+fanLabel(fan)+"\n"+performanceLabel(performance); }
    String status() { return (docked?"В доке":"Портативный режим")+" · "+(rate==0?"частота без изменения":rateLabel(rate))+" · "+fanLabel(fan); }
}
