/******************************************************************************
 * File:    BarcodeMIDlet.java
 * Author:  Rob Grmek
 * Project: Barcode Reader
 * Desc:    This MIDlet will perform the following sequence of actions:
 *              1. Program starts - displays notice if the device supports video
 *                 snapshots.
 *              2. Program captures an image using Java's Mobile Media API.
 *              3. Program uploads the image to the web server.
 *              4. Web server processes the image and attempts to read the code.
 *                 It then will display a page which the MIDlet will read and
 *                 interpret.
 *              5. If the scanning was successful, it will display the barcode
 *                 number and format. If it was unsuccessful, an error message
 *                 will be displayed prompting the user to try again.
 *          The user may continue to do so as many time as he or she wishes.
 ******************************************************************************/

import java.io.IOException;
import java.util.Hashtable;
import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;

public class BarcodeMIDlet extends MIDlet implements CommandListener {

    private Display display; 
    private Form mainForm;      // the main form for displaying information
    private Form stagingForm;   // form which displays the image captured
    private Player player;      // player utilizes mobile device's camera
    private VideoControl videoControl;  // for capturing video
    private Image image;        // the image taken by the camera
    private byte[] imageBytes;  // the same image as an array of bytes

    // Commands:
    private Command backCommand = new Command("Back", Command.BACK, 0);
    private Command cameraCommand = new Command("Take Image", Command.SCREEN, 0);
    private Command captureCommand = new Command("Capture", Command.SCREEN, 0);
    private Command sendCommand = new Command("Send", Command.SCREEN, 0);
    private Command exitCommand = new Command("Exit", Command.EXIT, 0);

    /***************************************************************************
     * Default constructor; will:
     *    - Create the main form
     *    - Check if the mobile device supports taking video snapshots
     **************************************************************************/
    public BarcodeMIDlet() {

        // Initialize the form which will display information such as:
        //  - message that the device does not support video snapshots
        //  - barcode information
        //  - prompts for user to re-take an image
        mainForm = new Form("Barcode Reader");
        mainForm.addCommand(exitCommand);
        mainForm.setCommandListener(this);

        // Check if the mobile device supports video snapshots
        String supports = System.getProperty("video.snapshot.encodings");
        String message = ""; // the message to be displayed
        if (supports != null && supports.length() > 0) {
            message = "Camera is ready to take pictures.";
            mainForm.addCommand(cameraCommand);
        } else {
            message = "This device does not support image capturing.";
        }
        mainForm.append(message);

        display = Display.getDisplay(this); // retrieve display object
    } // BarcodeMIDlet() default constructor

    /***************************************************************************
     * This method is used for:
     *    - Initializing and starting the player
     *    - Displaying the canvas which provides a GUI for displaying video
     *      of where the camera is being pointed at. Once the user has correctly
     *      aligned the camera with the barcode, he or she may capture an image.
     **************************************************************************/
    private void showCamera() {
        try {
            // Initialize the player
            player = Manager.createPlayer("capture://video");
            player.realize();
            videoControl = (VideoControl) player.getControl("VideoControl");

            // Display the canvas which displays visuals from the mobile
            // device's camera and allows the user to take a snapshot of it
            Canvas canvas = new CameraCanvas(this, videoControl);
            canvas.addCommand(backCommand);
            canvas.addCommand(captureCommand);
            canvas.setCommandListener(this);
            display.setCurrent(canvas);

            // Start the player
            player.start();
        } catch (IOException ioe) {
        } catch (MediaException me) {
        }
    } // showCamera() method

    /***************************************************************************
     * This method is used for:
     *    - Capturing an image using the mobile device's camera
     *    - Once the image is taken, will redirect the user to a staging form.
     *      This form displays the image which the user can review.
     *      The user then may attempt to send the image to the web server for
     *      processing or go back and take another image.
     **************************************************************************/
    public void captureImage() {
        try {
            // Get the image
            imageBytes  = videoControl.getSnapshot("encoding=png&width=280&height=260");
            //imageBytes  = videoControl.getSnapshot("encoding=jpeg&width=160&height=120");
            //imageBytes = videoControl.getSnapshot(null);
            image = Image.createImage(imageBytes, 0, imageBytes.length);

            // Create the form for the staging process
            stagingForm = new Form("Image Taken");
            stagingForm.addCommand(backCommand);
            stagingForm.addCommand(sendCommand);
            stagingForm.setCommandListener(this);
            stagingForm.append(image);

            // Go to the staging form
            display.setCurrent(stagingForm);

            // Shut down the player
            player.close();
            player = null;
            videoControl = null;
        } catch (MediaException me) {
        }
    } // captureImage() method

    /***************************************************************************
     * This method is used for:
     *    - Transmitting the image as a stream of bytes as part of a HTTP POST
     *      request to the web server.
     *    - Once the image has been processed, the web server will display a
     *      message indicated whether it was unsuccessful or successful.
     *    - The method will then read the server's response and display an alert
     *      to the user with a message indicating success or failure.
     *    - The user will be redirect back to the main form with the barcode
     *      information being display if it was successful or with a prompt to
     *      the user to try again if it was unsuccessful.
     **************************************************************************/
    public void sendToServer() {

        // For additional parameters to be passed to the POST request
        Hashtable params = new Hashtable();
        params.put("product[title]", "Test");
        try {
            HttpMultipartRequest req = new HttpMultipartRequest(
                    "http://barcodereader.oncloud.org/products/",
                    params,
                    //"product[image]", "barcode.jpg", "image/jpeg", imageBytes);
                    "product[image]", "barcode.png", "image/png", imageBytes);

            // Send the request and receive the server's response
            byte[] res = req.send();
            String response = new String(res);
            String title, message, productInfo;

            // Process the response
            if (response.startsWith("Success!")) {
                // Successful; display an alert indicating so and then redirect
                // to the main page which displays the product's barcode info
                title = "Success!";
                message = "The barcode was successfully read.";
                productInfo = response.substring(9); // read the barcode info
                mainForm.deleteAll();
                mainForm.setTitle("Product Information");
                mainForm.append(productInfo); // add the barcode info to form
            } else {
                // Unsuccessful at processing image; prompt the user to take
                // another image
                title = "Failure to process image!";
                message = response;
                mainForm.deleteAll();
                mainForm.setTitle("Barcode Reader");
                mainForm.append("Please try and re-take an image.");
            }
            
            // Display the response and redirect back to the main form
            Alert alert = new Alert(title, message, null, AlertType.INFO);
            alert.setTimeout(Alert.FOREVER);
            display.setCurrent(alert, mainForm);

        } catch (Exception e) {
            Alert alert = new Alert("Info", "Exception occured: " + e,
                    null, AlertType.INFO);
            alert.setTimeout(Alert.FOREVER);
            display.setCurrent(alert, mainForm);
        }
    } // sendToServer() method

   
    public void startApp() {
        display.setCurrent(mainForm);
    }

    public void pauseApp() {
    }

    public void destroyApp(boolean unconditional) {
    }

    public void commandAction(Command c, Displayable d) {
        if (d == mainForm && c == exitCommand) {
            destroyApp(true);
            notifyDestroyed();
        } else if (c == cameraCommand || (c == backCommand && d == stagingForm)) {
            showCamera();
        } else if (c == backCommand) {
            display.setCurrent(mainForm);
        } else if (c == captureCommand) {
            captureImage();
        } else if (c == sendCommand) {
            sendToServer();
        }
    }
}
