package controllers;


import models.IcdResultSet;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import services.SearchService;
import views.html.index;
import views.html.lic;

public class Application extends Controller {

    private SearchService searchService;


    public Application() {
        try {
            searchService = new SearchService();
        } catch (Exception ex) {
            Logger.error("Construction failed due to " + ex.toString());
        }
    }

    public Result index() {

        Form<models.SearchCriteria> userForm = Form.form(models.SearchCriteria.class);
        return ok(index.render("Also you can tap a button below to begin.", userForm, new IcdResultSet()));
    }

    public Result lic()
    {
        return ok(lic.render());
    }

    public Result search() {

        Form<models.SearchCriteria> userForm = Form.form(models.SearchCriteria.class).bindFromRequest();
        models.SearchCriteria c = userForm.get();
        try {
            if (c.filter == null || c.filter.equals("")) {
                return ok(index.render("Also you can tap a button below to begin.", userForm, new IcdResultSet()));
            }

            IcdResultSet codes = searchService.findDescription(c.filter);
            String s = "";
            int count = codes.codeValues.size();
            if (count > 1) s = "s";

            String msg = String.format("Prefect! Only %d code%s for %s.", count, s, c.filter);

            if (count > 20) {
                msg = String.format("Wow! %d code%s for %s. Tap a button below to quickly narrow the search.", count, s, c.filter);
            }
            else if (count == 0)
            {
                msg = String.format("Sorry! Nothing for %s. Please start over.", c.filter);
            }

            return ok(index.render(msg, userForm, codes));
        } catch (Exception e) {
            String msg = e.toString();
            return ok(index.render("Oops!, Even a monkey could fall from a tree condition: " +
                    msg, userForm, new IcdResultSet()));
        }
    }

}
