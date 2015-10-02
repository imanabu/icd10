package controllers;


import play.*;
import play.mvc.*;

import services.SearchService;
import views.html.*;


import java.io.File;
import java.net.URL;

public class Application extends Controller {

    private SearchService searchService;

    public Application()
    {
        try {
            searchService = new SearchService();
        }
        catch(Exception ex) {
            Logger.error("Construction failed due to " + ex.toString());
        }
    }

    public Result index() {

        try {
            searchService.findDescription("'Pressure ulcer'");
            return ok(index.render("Your new application is ready."));
        } catch (Exception e) {
            String msg = e.toString();
            return ok(index.render("Oh well..." + msg));
        }
    }

}
