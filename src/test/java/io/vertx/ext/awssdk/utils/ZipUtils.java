package io.vertx.ext.awssdk.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public interface ZipUtils {

    static byte[] zip(String prefix, String fileName) {
        final InputStream is = ClassLoader.getSystemResourceAsStream(prefix + "/" + fileName);
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final ZipOutputStream zos = new ZipOutputStream(bos);
        try {
            zos.putNextEntry(new ZipEntry(fileName));
            int length;
            byte[] buffer = new byte[2048];
            while ((length = is.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
            zos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bos.toByteArray();
    }


}
