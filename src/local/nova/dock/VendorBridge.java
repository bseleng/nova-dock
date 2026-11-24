package local.nova.dock;

import android.os.IBinder;
import android.os.Parcel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Retroid's stock IPC protocol. This app exposes no IPC endpoint for commands. */
final class VendorBridge {
    private IBinder binder;
    private IBinder service() throws Exception {
        if (binder == null || !binder.isBinderAlive()) {
            Class<?> manager = Class.forName("android.os.ServiceManager");
            binder = (IBinder) manager.getMethod("getService", String.class).invoke(null, "PServerBinder");
        }
        if (binder == null || !binder.isBinderAlive()) throw new IllegalStateException("Сервис Retroid недоступен");
        return binder;
    }
    boolean available() {
        try { return service() != null; } catch (Exception e) { return false; }
    }
    String probe() { return execute("id"); }
    void write(String key, String value) {
        if (!Arrays.asList(Retroid.KEYS).contains(key)) throw new IllegalArgumentException("Недопустимый ключ");
        if (value != null && !value.matches("[0-9]+(?:\\.[0-9]+)?")) throw new IllegalArgumentException("Недопустимое значение");
        String output = execute(value == null ? "settings delete system " + key : "settings put system " + key + " " + value);
        if (output.contains("Exception") || output.contains("Permission denial") || output.contains("Error")) throw new IllegalStateException(output);
    }
    private String execute(String command) {
        Parcel data=Parcel.obtain(), reply=Parcel.obtain();
        try {
            data.writeStringArray(new String[]{command,"1"});
            if (!service().transact(0,data,reply,0)) throw new IllegalStateException("Сервис Retroid не принял команду");
            byte[] bytes=reply.createByteArray();
            if (bytes==null) throw new IllegalStateException("Нет ответа сервиса Retroid");
            return new String(bytes,StandardCharsets.UTF_8).trim();
        } catch (Exception e) { throw new IllegalStateException("Сервис Retroid: "+e.getMessage(),e); }
        finally { data.recycle(); reply.recycle(); }
    }
}
