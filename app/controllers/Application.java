package controllers;


import models.IcdResultSet;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import services.SearchService;
import views.html.index;

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
        return ok(index.render("Good to go", userForm, new IcdResultSet()));
    }

    public Result search() {

        Form<models.SearchCriteria> userForm = Form.form(models.SearchCriteria.class).bindFromRequest();
        models.SearchCriteria c = userForm.get();
        try {
            if (c.filter == null || c.filter.equals("")) {
                return ok(index.render("Please type in the search term", userForm, new IcdResultSet()));
            }

            IcdResultSet codes = searchService.findDescription(c.filter);
            String s = "";
            int count = codes.codeValues.size();
            if (count > 1) s = "s";
            String msg = String.format("%d code%s found for %s.", count, s, c.filter);
            return ok(index.render(msg, userForm, codes));
        } catch (Exception e) {
            String msg = e.toString();
            return ok(index.render("Oops!, Even a monkey could fall from a tree condition: " +
                    msg, userForm, new IcdResultSet()));
        }
    }

}
