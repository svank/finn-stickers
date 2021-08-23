package net.samvankooten.finnstickers;

public class Constants {
    public static final String CONTENT_URI_ROOT =
            String.format("content://%s/", StickerProvider.class.getName());
    public static final String URL_BASE = "https://samvankooten.net/finn_stickers/v4/";
    public static final String PACK_LIST_URL = URL_BASE + "sticker_pack_list.json";
    public static final String URL_REMOVED_STICKER_DIR = "removed";
    public static final String USER_STICKERS_DIR = "user_stickers";
    
    public static final int RESTORE_JOB_ID = 1234;
    
    public static final String DIR_FOR_SHARED_FILES = "shared";
}
