package cat.narezany.margyt;

import android.content.Context;
import android.content.res.Resources;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * TikTok's pictures with nothing to recolour, by resource id.
 *
 * Most of them have no red in them, and finding that out means reading all their pixels on the main
 * thread. The answer can't change for a given TikTok and accent, so it's kept: after the first time a
 * picture is seen, not in this run but ever, asking costs one look in a set.
 */
final class PlainArt {

    private PlainArt() {}

    private static final Set<Integer> known = new HashSet<>();
    private static int loadedFor;
    private static boolean loaded, dirty, saving;

    private static File file(Context context) {
        return new File(context.getFilesDir(), "margyt/plain-art");
    }

    /** One bit for a dark screen: a picture can draw from the theme and be something else there. */
    private static int key(Resources resources, int id) {
        boolean night = resources != null
                && (resources.getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return night ? id ^ 0x80000000 : id;
    }

    static synchronized boolean has(Resources resources, int id, int target) {
        if (id == 0) return false;
        if (!loaded || loadedFor != target) load(target);
        return known.contains(key(resources, id));
    }

    static synchronized void add(Resources resources, int id, int target) {
        if (id == 0) return;
        if (!loaded || loadedFor != target) load(target);
        if (!known.add(key(resources, id))) return;
        dirty = true;
        if (saving) return;
        saving = true;
        // one write for the whole run of pictures a new screen brings
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> Net.away("plain art", PlainArt::save), 15000);
    }

    private static void load(int target) {
        known.clear();
        loaded = true;
        loadedFor = target;
        dirty = false;
        Context context = Margy.context();
        if (context == null) return;
        File file = file(context);
        if (!file.isFile()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (!Version.TIKTOK.equals(in.readUTF()) || in.readInt() != target) return;
            int count = in.readInt();
            for (int i = 0; i < count; i++) known.add(in.readInt());
        } catch (Throwable error) {
            known.clear();
        }
    }

    private static void save() {
        int[] all;
        int target;
        synchronized (PlainArt.class) {
            saving = false;
            if (!dirty) return;
            dirty = false;
            target = loadedFor;
            all = new int[known.size()];
            int at = 0;
            for (int one : known) all[at++] = one;
        }
        Context context = Margy.context();
        if (context == null) return;
        File file = file(context);
        File temp = new File(file.getPath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
            out.writeUTF(Version.TIKTOK);
            out.writeInt(target);
            out.writeInt(all.length);
            for (int one : all) out.writeInt(one);
        } catch (Throwable error) {
            temp.delete();
            return;
        }
        temp.renameTo(file);
    }
}
