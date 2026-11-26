package local.nova.dock;

/** Stock write order with rollback to this operation's live state, not the app's first-use original. */
final class OutputTransaction {
    enum Property { SELECT, WIDTH, HEIGHT, FPS }
    interface Port {
        void window(Integer width,Integer height) throws Exception;
        void property(Property property,String value) throws Exception;
        void verify() throws Exception;
    }
    static final class Before {
        final boolean overridden;
        final int width,height;
        final String[] properties;
        Before(boolean overridden,int width,int height,String[] properties) {
            if (properties.length!=4) throw new IllegalArgumentException("invalid_value");
            this.overridden=overridden; this.width=width; this.height=height; this.properties=properties.clone();
        }
    }
    static void apply(Port port,Before before,OutputMode.Preset preset,OutputMode.Aspect aspect,boolean changeMode,boolean changeAspect) throws Exception {
        try {
            if (changeAspect) port.window(aspect.width,aspect.height);
            if (changeMode) {
                port.property(Property.SELECT,preset==OutputMode.Preset.ADAPTIVE?"0":"1");
                if (preset!=OutputMode.Preset.ADAPTIVE) {
                    port.property(Property.WIDTH,Integer.toString(preset.width(aspect)));
                    port.property(Property.HEIGHT,Integer.toString(preset.height(aspect)));
                    port.property(Property.FPS,Integer.toString(preset.fps));
                }
            }
            port.verify();
        } catch (Exception failure) {
            if (changeAspect) try { port.window(before.overridden?before.width:null,before.overridden?before.height:null); } catch (Exception rollback) { failure.addSuppressed(rollback); }
            if (changeMode) for (Property property:Property.values()) try { port.property(property,before.properties[property.ordinal()]); } catch (Exception rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
}
