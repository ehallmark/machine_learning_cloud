package seeding.ai_db_updater.tools;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by ehallmark on 1/3/17.
 */
public class ZipHelper {
    public static void unzip(InputStream inputStream, OutputStream destination) {
        try {
            byte[] buf = new byte[1024];
            ZipInputStream zipinputstream = null;
            ZipEntry zipentry;
            zipinputstream = new ZipInputStream(inputStream);

            zipentry = zipinputstream.getNextEntry();
            while (zipentry != null) {
                int n;

                while ((n = zipinputstream.read(buf, 0, 1024)) > -1) {
                    destination.write(buf, 0, n);
                }

                destination.close();
                zipinputstream.closeEntry();
                zipentry = zipinputstream.getNextEntry();

            }

            zipinputstream.close();
        } catch (Exception e) {
            throw new IllegalStateException("Can't unzip input stream", e);
        }
    }
}
