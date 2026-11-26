package local.nova.dock;

/** The exact stock Retroid output choices; no private RPSettings preference is involved. */
final class OutputMode {
    enum Aspect {
        FOUR_THREE(960,1280), SIXTEEN_NINE(1080,1920);
        final int width,height;
        Aspect(int width,int height) { this.width=width; this.height=height; }
        static Aspect of(int width,int height) {
            return width*16==height*9 || width*9==height*16?SIXTEEN_NINE:FOUR_THREE;
        }
    }
    enum Preset {
        ADAPTIVE(0,0,0), UHD60(3840,2160,60), QHD120(2560,1440,120), QHD144(2560,1440,144),
        P60(0,0,60), P120(0,0,120), P240(0,0,240);
        final int width,height,fps;
        Preset(int width,int height,int fps) { this.width=width; this.height=height; this.fps=fps; }
        int width(Aspect aspect) { return width!=0?width:aspect==Aspect.FOUR_THREE?1280:1920; }
        int height(Aspect aspect) { return height!=0?height:aspect==Aspect.FOUR_THREE?960:1080; }
        static Preset infer(String select,int width,int height,int fps,Aspect aspect) {
            if ("0".equals(select)) return ADAPTIVE;
            if (!"1".equals(select)) return null;
            for (Preset preset:values()) if (preset!=ADAPTIVE && preset.width(aspect)==width && preset.height(aspect)==height && preset.fps==fps) return preset;
            return null;
        }
    }
    static final class Choice {
        final Preset preset;
        final Aspect aspect;
        final boolean changeMode,changeAspect;
        Choice(Preset preset,Aspect aspect,boolean changeMode,boolean changeAspect) {
            if (aspect==null || changeMode && preset==null || !changeMode && !changeAspect) throw new IllegalArgumentException("invalid_value");
            this.preset=preset; this.aspect=aspect; this.changeMode=changeMode; this.changeAspect=changeAspect;
        }
        static Choice merge(Choice previous,Choice next) {
            Aspect aspect=next.changeAspect?next.aspect:previous.changeAspect?previous.aspect:next.aspect;
            Preset preset=next.changeMode?next.preset:previous.changeMode?previous.preset:next.preset;
            return new Choice(preset,aspect,previous.changeMode || next.changeMode,previous.changeAspect || next.changeAspect);
        }
    }
}
