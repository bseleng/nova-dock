package local.nova.dock;

import android.os.IBinder;
import android.os.Parcel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Retroid's stock IPC protocol. This app exposes no IPC endpoint for commands. */
final class VendorBridge {
    enum DpProperty {
        SELECT("persist.vendor.dp.select"), WIDTH("persist.vendor.dp.hdisplay"), HEIGHT("persist.vendor.dp.vdisplay"), FPS("persist.vendor.dp.fps");
        final String key;
        DpProperty(String key) { this.key=key; }
    }
    private IBinder binder;
    private IBinder service() throws Exception {
        if (binder == null || !binder.isBinderAlive()) {
            Class<?> manager = Class.forName("android.os.ServiceManager");
            binder = (IBinder) manager.getMethod("getService", String.class).invoke(null, "PServerBinder");
        }
        if (binder == null || !binder.isBinderAlive()) throw new Failure("vendor_unavailable");
        return binder;
    }
    boolean available() {
        try { return service() != null; } catch (Exception e) { return false; }
    }
    String probe() { return execute("id"); }
    void write(String key, String value) {
        if (!Arrays.asList(Retroid.KEYS).contains(key)) throw new Failure("invalid_value");
        if (value != null && !value.matches("[0-9]+(?:\\.[0-9]+)?")) throw new Failure("invalid_value");
        execute(value == null ? "settings delete system " + key : "settings put system " + key + " " + value);
    }
    String screenOff() { String value=execute("settings get system close_screen_when_output_video"); return "null".equals(value)?null:value; }
    void screenOff(Boolean value) {
        execute(value==null?"settings delete system close_screen_when_output_video":"settings put system close_screen_when_output_video "+(value?"1":"0"));
    }
    String refreshOverlay() { return execute("service call SurfaceFlinger 1034 i32 2"); }
    void refreshOverlay(boolean enabled) { execute(enabled?"service call SurfaceFlinger 1034 i32 1":"service call SurfaceFlinger 1034 i32 0"); }
    String property(DpProperty property) { return execute("getprop "+property.key); }
    void property(DpProperty property,String value) {
        if (value==null || !value.matches("[0-9]{0,8}")) throw new Failure("invalid_value");
        execute("setprop "+property.key+" "+(value.isEmpty()?"''":value));
    }
    String windowSize() { return execute("wm size -d 0"); }
    void windowSize(Integer width,Integer height) {
        if (width==null && height==null) { execute("wm size reset -d 0"); return; }
        if (width==null || height==null || width<1 || height<1 || width>16384 || height>16384) throw new Failure("invalid_value");
        execute("wm size "+width+"x"+height+" -d 0");
    }
    private String execute(String command) {
        Parcel data=Parcel.obtain(), reply=Parcel.obtain();
        try {
            data.writeStringArray(new String[]{command,"1"});
            if (!service().transact(0,data,reply,0)) throw new Failure("command_failed");
            byte[] bytes=reply.createByteArray();
            if (bytes==null) throw new Failure("command_failed");
            String output=new String(bytes,StandardCharsets.UTF_8).trim();
            if (output.contains("Exception") || output.contains("Permission denial") || output.contains("Error:")) throw new Failure("command_failed",output);
            return output;
        } catch (Failure e) { throw e; }
        catch (Exception e) { throw new Failure("vendor_unavailable",e.getClass().getSimpleName()); }
        finally { data.recycle(); reply.recycle(); }
    }
}
