package server.tools;

import spark.Request;

import java.util.ArrayList;
import java.util.Stack;

/**
 * Created by ehallmark on 2/15/17.
 */
public class BackButtonHandler {
    private Stack<Request> backStack = new Stack<>();
    private Stack<Request> forwardStack = new Stack();
    private Request currentRequest;

    public BackButtonHandler(Request currentRequest){
        this.currentRequest=currentRequest;
    }

    public void addRequest(Request req) {
        if(req==null)throw new NullPointerException("request input is null");
        backStack.add(currentRequest);
        currentRequest=req;
    }

    public Request goBack() {
        if(backStack.isEmpty()) return null;
        Request back = backStack.pop();
        forwardStack.add(currentRequest);
        currentRequest=back;
        return back;
    }

    public Request goForward() {
        if(forwardStack.isEmpty()) return null;
        Request forward = forwardStack.pop();
        backStack.add(currentRequest);
        currentRequest=forward;
        return forward;
    }
}
