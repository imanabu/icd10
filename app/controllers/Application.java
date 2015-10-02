package controllers;


import play.*;
import play.data.Form;
import play.mvc.*;

import services.SearchService;
import views.html.*;


import java.io.File;
import java.net.URL;
import java.util.Map;

public class Application extends Controller {

    private SearchService searchService;
    private Form<models.SearchCriteria> userForm = Form.form(models.SearchCriteria.class);

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

        return ok(index.render("Good to go", userForm, null));
    }

    public Result search()
    {
        try {
            models.SearchCriteria c = userForm.bindFromRequest().get();
            if (c.filter == null || c.filter.equals(""))
            {
                return ok(index.render("Please type in the search term", userForm, null));
            }

            String quoted = String.format("'%s'", c.filter);
            Map<String, String> codes = searchService.findDescription(quoted);
            String msg = String.format("%d terms found for %s.", codes.size(), quoted);
            return ok(index.render(msg, userForm, codes));
        } catch (Exception e) {
            String msg = e.toString();
            return ok(index.render("Oh well..." + msg, userForm, null));
        }
    }

}
