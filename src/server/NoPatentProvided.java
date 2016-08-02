package server;

/**
 * Created by ehallmark on 7/27/16.
 */
// inner json classes
public class NoPatentProvided extends ServerResponse {
    NoPatentProvided(){
        super("","Please provide a patent number.",null);
    }
}