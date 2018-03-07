package user_interface.server.tools;

import java.util.Stack;

/**
 * Created by ehallmark on 2/15/17.
 */
public class BackButtonHandler<T> {
    private Stack<T> backStack = new Stack<>();
    private Stack<T> forwardStack = new Stack();
    private T currentRequest;

    public void addRequest(T req) {
        if(req==null)throw new NullPointerException("request input is null");
        if(currentRequest!=null)backStack.add(currentRequest);
        currentRequest=req;
    }

    public T goBack() {
        if(currentRequest!=null)forwardStack.add(currentRequest);
        if(backStack.isEmpty()) return null;
        T back = backStack.pop();
        currentRequest=back;
        return back;
    }

    public T goForward() {
        if(currentRequest!=null)backStack.add(currentRequest);
        if(forwardStack.isEmpty()) return null;
        T forward = forwardStack.pop();
        currentRequest=forward;
        return forward;
    }
}
