package net.samvankooten.finnstickers;

import java.util.ArrayList;

/**
 * Created by sam on 9/23/17.
 */

public class Sticker {

    private String filename;
    private ArrayList keywords;

    public Sticker(){
        keywords = new ArrayList();
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public ArrayList getKeywords() {
        return keywords;
    }

    public void setKeywords(ArrayList keywords) {
        this.keywords = keywords;
    }
    
    public void addKeyword(String keyword){
        keywords.add(keyword);
    }
}
