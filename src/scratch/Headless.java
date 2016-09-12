package scratch;

/**
 * Created by ehallmark on 9/12/16.
 */
/*
 *  Copyright 2010 Blue Lotus Software.
 *  Copyright 2010 John Yeary.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

        import java.awt.*;
        import java.io.*;
        import javax.imageio.ImageIO;
        import java.awt.image.BufferedImage;

/**
 *
 * @author John Yeary
 * @version 1.0
 */
public class Headless {

    private static BufferedImage generateRectangle(Component component, int width, int height) {
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics graphics = bufferedImage.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.ORANGE);
        graphics.fill3DRect(50, 50, 300, 300, true);
        component.paint(graphics);
        return bufferedImage;
    }

    private static BufferedImage generateRectangle(Component component) {
        return generateRectangle(component, 400, 400);
    }

    private static BufferedImage generateCylinder(int width, int height) {
        Panel panel = new Panel();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics graphics = image.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.ORANGE);
        graphics.drawOval(100, 100, 50, 75);
        graphics.fillOval(200, 100, 50, 75);
        graphics.drawLine(125, 100, 225, 100);
        graphics.drawLine(125, 175, 225, 175);
        panel.paint(graphics);
        return image;
    }

    private static boolean save(BufferedImage image, Component component) {
        boolean success = false;
        try {
            ImageIO.write(image, "gif", new FileOutputStream(component.getClass().getSimpleName() + ".gif"));
            success = true;
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace(System.err);
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
        return success;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        boolean headless = GraphicsEnvironment.isHeadless();
        System.out.println("Headless: " + headless);
        Toolkit tk = Toolkit.getDefaultToolkit();
        tk.beep();

        BufferedImage bufferedImage = null;
        boolean success = false;

        // ---------------------------------------------------------------------------------------------- //
        Component component = new Component() {

            private static final long serialVersionUID = 3109256773218160485L;
        };
        Component c = TreeDrawing.getSampleTree();
        Canvas canvas = new Canvas();
        Panel panel = new Panel();
        // ---------------------------------------------------------------------------------------------- //

        // Drawing Examples
        bufferedImage = Headless.generateRectangle(component);
        success = Headless.save(bufferedImage, component);
        System.out.println("Created " + component.getClass().getSimpleName() + " : " + success);
        success = false;

        // Drawing Examples
        bufferedImage = Headless.generateRectangle(c);
        success = Headless.save(bufferedImage, c);
        System.out.println("Created " + c.getClass().getSimpleName() + " : " + success);
        success = false;

        bufferedImage = Headless.generateRectangle(canvas);
        success = Headless.save(bufferedImage, canvas);
        System.out.println("Created " + canvas.getClass().getSimpleName() + " : " + success);
        success = false;

//        bufferedImage = Headless.generateRectangle(panel);
//        success = Headless.save(bufferedImage, panel);
//        System.out.println("Created " + panel.getClass().getSimpleName() + " : " + success);
//        success = false;

        bufferedImage = Headless.generateCylinder(400, 400);
        success = Headless.save(bufferedImage, panel);
        System.out.println("Created " + panel.getClass().getSimpleName() + " : " + success);
        success = false;
        // ---------------------------------------------------------------------------------------------- //

    }
}

