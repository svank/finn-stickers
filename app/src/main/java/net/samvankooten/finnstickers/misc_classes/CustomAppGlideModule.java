package net.samvankooten.finnstickers.misc_classes;

import android.content.Context;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

@GlideModule
public class CustomAppGlideModule extends AppGlideModule {

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        // We mostly deal with small images in this app. By default, Glide will decode a local
        // image, scale & transform it as needed, and then cache that scaled & transformed
        // image on disk. For this app, that means we that after scrolling through images in
        // StickerPackViewer, we'll have cache usage ~equal to the storage usage of the stickers
        // themselves, all to save a tiny bit of time re-scaling small images in the future.
        // It doesn't seem worth it to me---the app is otherwise light on storage usage, and I'd
        // kinda like to keep it that way. So I'm disabling disk cache here, and I'll re-enable
        // it for any loads involving remote resources.
        builder.setDefaultRequestOptions(
                new RequestOptions().diskCacheStrategy(DiskCacheStrategy.NONE)
        );
    }
}
