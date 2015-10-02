package controllers;


import models.IcdResultSet;
import play.*;
import play.data.Form;
import play.mvc.*;

import services.SearchService;
import views.html.*;


import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import models.CodeValue;

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

        Form<models.SearchCriteria> userForm = Form.form(models.SearchCriteria.class);
        return ok(index.render("Good to go", userForm, new IcdResultSet()));
    }

    public Result search()
    {

        Form<models.SearchCriteria> userForm = Form.form(models.SearchCriteria.class).bindFromRequest();
        models.SearchCriteria c = userForm.get();
        try {
            if (c.filter == null || c.filter.equals(""))
            {
                return ok(index.render("Please type in the search term", userForm, new IcdResultSet()));
            }

            IcdResultSet codes = searchService.findDescription(c.filter);
            String msg = String.format("%d codes found for %s.", codes.codeValues.size(), c.filter);
            return ok(index.render(msg, userForm, codes));
        } catch (Exception e) {
            String msg = e.toString();
            return ok(index.render("Oh well..." + msg, userForm, new IcdResultSet()));
        }
    }

}
