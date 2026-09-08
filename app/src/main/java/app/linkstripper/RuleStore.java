package app.linkstripper;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Samples never enter storage. Only explicitly saved templates and host permissions persist. */
public final class RuleStore {
    private static String cachedJson;
    private static List<SiteRule> cachedRules;
    public static synchronized List<SiteRule> load(Context context) {
        String json=context.getSharedPreferences("site_rules",0).getString("rules","[]");
        if(json.equals(cachedJson))return new ArrayList<>(cachedRules);
        List<SiteRule> rules=new ArrayList<>();
        try {
            JSONArray items=new JSONArray(json);
            for(int i=0;i<items.length();i++) {
                JSONObject o=items.getJSONObject(i);
                rules.add(new SiteRule(o.getString("id"),o.getString("name"),o.getString("sourceHost"),o.getString("sourcePath"),o.getString("targetHost"),o.getString("targetPath"),o.getString("outputHost"),o.getString("outputPath"),o.getString("keepQuery"),o.getString("allowedHosts"),o.getBoolean("resolveFirst"),o.getBoolean("enabled")));
            }
        } catch(Exception e) { throw new IllegalStateException("Saved rules could not be read. Open Site rules to reset them."); }
        cachedJson=json;cachedRules=new ArrayList<>(rules);
        return rules;
    }
    public static void save(Context context,List<SiteRule> rules) {
        if(rules.size()>100) throw new IllegalArgumentException("Up to 100 custom rules are supported.");
        JSONArray items=new JSONArray();
        try { for(SiteRule r:rules) {
            JSONObject o=new JSONObject();
            o.put("id",r.id()); o.put("name",r.name()); o.put("sourceHost",r.sourceHost()); o.put("sourcePath",r.sourcePath());
            o.put("targetHost",r.targetHost()); o.put("targetPath",r.targetPath()); o.put("outputHost",r.outputHost()); o.put("outputPath",r.outputPath());
            o.put("keepQuery",r.keepQuery()); o.put("allowedHosts",r.allowedHosts()); o.put("resolveFirst",r.resolveFirst()); o.put("enabled",r.enabled()); items.put(o);
        }} catch(JSONException e) { throw new IllegalArgumentException("Could not encode rules."); }
        var prefs=context.getSharedPreferences("site_rules",0);
        prefs.edit().putString("previous",prefs.getString("rules","[]")).putString("rules",items.toString()).apply();
    }
    public static void undo(Context context) { var prefs=context.getSharedPreferences("site_rules",0); String before=prefs.getString("previous","[]"); String now=prefs.getString("rules","[]"); prefs.edit().putString("rules",before).putString("previous",now).apply(); }
    public static SiteRule find(Context context,String url) {
        List<SiteRule> rules=load(context);if(rules.isEmpty())return null;
        java.net.URI uri;
        try{uri=SiteRule.url(url);}catch(IllegalArgumentException e){return null;}
        for(SiteRule r:rules)if(r.matches(uri))return r;return null;
    }
}
