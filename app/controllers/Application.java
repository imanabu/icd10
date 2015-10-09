package controllers;


import models.IcdResultSet;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import services.SearchService;
import views.html.index;
import views.html.lic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Application extends Controller {

    private SearchService searchService;
    private List<String> addTerms;


    public Application() {
        try {
            searchService = new SearchService();
            addTerms = new ArrayList<>();
            String list = "right left proximal medial distal peripheral exterm* upper lower lateral anterior posterior frontal " +
                    "extra inner outer head ear eye nose mouth neck chest back arm buttock thigh leg foot";
            String[] split = list.split(" ");
            Collections.addAll(addTerms, split);
        } catch (Exception ex) {
            Logger.error("Construction failed due to " + ex.toString());
        }
    }

    public Result index() {

        Form<models.SearchCriteria> userForm = Form.form(models.SearchCriteria.class);
        return ok(index.render("Also you can add a few applicable anatomical terms below.", userForm, new IcdResultSet(),
                addTerms
        ));
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
                return ok(index.render("Also you can tap a few anatomical terms to demo.", userForm, new IcdResultSet(),
                        addTerms));
            }

            String[] terms = c.filter.trim().split(" ");

            if (terms.length <= 1) {
                if (addTerms.contains(terms[0])) {
                    return ok(index.render("Click one more term from below or type in a term above.", userForm, new IcdResultSet(),
                            addTerms));
                }
            }

            int detailThreshold = 75;

            IcdResultSet codes = searchService.findDescription(c.filter, detailThreshold);

            String s = "";
            int count = codes.codeValues.size();
            if (count > 1) s = "s";

            StringBuilder msg = new StringBuilder();

            List<String> addTerms2 = addTerms;

            if (count > 20) {
                codes.clearCodesIfTooMany(detailThreshold);
                msg.append(String.format("Wow! %d code%s for %s. Tap a button below to quickly narrow your search. ", count, s, c.filter));
            }
            else if (count == 0)
            {
                msg.append(String.format("Sorry! Nothing for %s. Please start over.", c.filter));
                addTerms2 = new ArrayList<>();
            }
            else
            {
                msg.append(String.format("Perfect! Only %d code%s for %s. Please scroll down to see the results. ", count, s, c.filter));
                addTerms2 = new ArrayList<>();
            }

            if (codes.subCodes.size() > 0)
            {
                msg.append("7th code ");

                int csLen = codes.subCodes.size() - 1;
                Iterator<String> it = codes.subCodes.iterator();
                for(int ci = 0; ci <= csLen; ci++) {
                    msg.append(it.next());
                    if (ci < csLen) msg.append(",");
                    else msg.append(" ");
                }

                msg.append("in effect!");
            }

            return ok(index.render(msg.toString(), userForm, codes, addTerms2));
        } catch (Exception e) {
            String msg = e.toString();
            return ok(index.render("Oops!, Even a monkey could fall from a tree condition: " +
                    msg, userForm, new IcdResultSet(),
                    addTerms));
        }
    }

}
