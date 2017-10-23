package net.samvankooten.finnstickers;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPack {
    public static final String TAG = "StickerPack";
    private String packname;
    private String iconurl;
    private String datafile;
    private File iconfile;

    public static StickerPack[] getStickerPacks(URL url, File iconDir) throws JSONException{
        Util.DownloadResult result;
        StickerPack[] list = null;

        // Get the data at the URL
        try {
            result = Util.downloadFromUrl(url);
        } catch (IOException e) {
            Log.e(TAG, "Error downloading sticker pack list", e);
            return list;
        }

        // Parse the list of packs out of the JSON data
        JSONObject json = new JSONObject(result.readString(20000));
        JSONArray packs = json.getJSONArray("packs");

        // Parse each StickerPack JSON object and download icons
        list = new StickerPack[packs.length()];
        for (int i = 0; i < packs.length(); i++) {
            JSONObject packData = packs.getJSONObject(i);
            StickerPack pack = new StickerPack(packData);
            list[i] = pack;

            File destination = new File(iconDir, pack.iconurl);
            try {
                URL iconURL = new URL(Util.getURLPath(url) + pack.iconurl);
                Util.downloadSticker(iconURL, destination);
                pack.setIconfile(destination);
            } catch (Exception e) {
                Log.e(TAG, "Difficulty downloading pack icon", e);
            }
        }

        return list;
    }

    public StickerPack(JSONObject data) throws JSONException {
        this.packname = data.getString("packname");
        this.iconurl = data.getString("iconurl");
        this.datafile = data.getString("datafile");
        this.iconfile = null;
    }

    public String getPackname() {
        return packname;
    }

    public void setPackname(String packname) {
        this.packname = packname;
    }

    public String getIconurl() {
        return iconurl;
    }

    public void setIconurl(String iconurl) {
        this.iconurl = iconurl;
    }

    public String getDatafile() {
        return datafile;
    }

    public void setDatafile(String datafile) {
        this.datafile = datafile;
    }

    public File getIconfile() {
        return iconfile;
    }

    public void setIconfile(File iconfile) {
        this.iconfile = iconfile;
    }
}
