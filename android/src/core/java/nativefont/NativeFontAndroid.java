package core.java.nativefont;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;

/**
 * Created by tian on 2016/10/2.
 */

public class NativeFontAndroid implements NativeFontListener {
    private HashMap<String, Typeface> fontFaces = new HashMap<String, Typeface>();
    private AndroidApplication androidApplication = (AndroidApplication) Gdx.app;
    @Override
    public Pixmap getFontPixmap(String txt, NativeFontPaint vpaint) {
        Paint paint = new Paint();

        if (!vpaint.getTTFName().equals("")) {
            // Typeface fontFace = fontFaces.get(vpaint.getTTFName());
            Gdx.app.log("app",Gdx.files.internal(vpaint.getTTFName()
                    + (vpaint.getTTFName().endsWith(".ttf") ? ""
                    : ".ttf")).file().getPath());
            Typeface fontFace = Typeface.createFromAsset(androidApplication.getAssets(),vpaint.getTTFName()
                    + (vpaint.getTTFName().endsWith(".ttf") ? ""
                    : ".ttf"));
            fontFaces.put(vpaint.getTTFName(), fontFace);
            paint.setTypeface(fontFace);
        }
        paint.setAntiAlias(true);
        paint.setTextSize(vpaint.getTextSize());
        Paint.FontMetrics fm = paint.getFontMetrics();
        int w = (int) paint.measureText(txt);
        int h = (int) (fm.descent - fm.ascent);
        if (w == 0) {
            w = h = vpaint.getTextSize();
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // 如果是描边类型
        if (vpaint.getStrokeColor() != null) {
            // 绘制外层
            paint.setColor(getColor(vpaint.getStrokeColor()));
            paint.setStrokeWidth(vpaint.getStrokeWidth()); // 描边宽度
            paint.setStyle(Paint.Style.FILL_AND_STROKE); // 描边种类
            paint.setFakeBoldText(true); // 外层text采用粗体
            canvas.drawText(txt, 0, -fm.ascent, paint);
            paint.setFakeBoldText(false);
        } else {
            paint.setUnderlineText(vpaint.getUnderlineText());
            paint.setStrikeThruText(vpaint.getStrikeThruText());
            paint.setFakeBoldText(vpaint.getFakeBoldText());
        }
        // 绘制内层
        paint.setStrokeWidth(0);
        paint.setColor(getColor(vpaint.getColor()));
        canvas.drawText(txt, 0, -fm.ascent, paint);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer);
        byte[] encodedData = buffer.toByteArray();
        Pixmap pixmap = new Pixmap(encodedData, 0, encodedData.length);
        bitmap = null;
        canvas = null;
        return pixmap;
    }

    private int getColor(Color color) {
        return (((((int) (color.a * 255.0f)) << 24) | (((int) (color.r * 255.0f)) << 16)) | (((int) (color.g * 255.0f)) << 8)) | ((int) (color.b * 255.0f));
    }
}
