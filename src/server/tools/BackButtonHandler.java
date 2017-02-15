package server.tools;

import spark.QueryParamsMap;
import spark.Request;

import java.util.ArrayList;
import java.util.Stack;

/**
 * Created by ehallmark on 2/15/17.
 */
public class BackButtonHandler {
    private Stack<QueryParamsMap> backStack = new Stack<>();
    private Stack<QueryParamsMap> forwardStack = new Stack();
    private QueryParamsMap currentRequest;

    public void addRequest(QueryParamsMap req) {
        if(req==null)throw new NullPointerException("request input is null");
        if(currentRequest!=null)backStack.add(currentRequest);
        currentRequest=req;
    }

    public QueryParamsMap goBack() {
        if(currentRequest!=null)forwardStack.add(currentRequest);
        if(backStack.isEmpty()) return null;
        QueryParamsMap back = backStack.pop();
        currentRequest=back;
        return back;
    }

    public QueryParamsMap goForward() {
        if(currentRequest!=null)backStack.add(currentRequest);
        if(forwardStack.isEmpty()) return null;
        QueryParamsMap forward = forwardStack.pop();
        currentRequest=forward;
        return forward;
    }
}
