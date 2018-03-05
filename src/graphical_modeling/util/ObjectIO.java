package graphical_modeling.util;

import java.io.*;

/**
 * Created by ehallmark on 4/24/17.
 */
public class ObjectIO<T> {
    public T load(File file) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
            return (T) ois.readObject();

        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void save(File file, Object obj) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
            oos.writeObject(obj);
            oos.flush();
            oos.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}