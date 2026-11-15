package nl.home.leanlauncher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.util.Random;

/** A pixel-art block (grass, stone or gold) used as a tile background. */
public class BlockDrawable extends Drawable {

    public static final int GRASS = 0, STONE = 1, GOLD = 2;
    private static final int N = 16; // 16x16 pixels per block

    private final int type;
    private final int seed;
    private final Paint p = new Paint();

    public BlockDrawable(int type, int seed) {
        this.type = type;
        this.seed = seed;
        p.setAntiAlias(false);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float cw = b.width() / (float) N, ch = b.height() / (float) N;
        Random r = new Random(seed * 31L + type);
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                p.setColor(colour(x, y, r.nextInt(3)));
                canvas.drawRect(b.left + x * cw, b.top + y * ch,
                        b.left + (x + 1) * cw + 0.5f, b.top + (y + 1) * ch + 0.5f, p);
            }
        }
        // dark outline
        p.setColor(0xFF1E1E1E);
        float o = Math.max(2, cw / 2);
        canvas.drawRect(b.left, b.top, b.right, b.top + o, p);
        canvas.drawRect(b.left, b.bottom - o, b.right, b.bottom, p);
        canvas.drawRect(b.left, b.top, b.left + o, b.bottom, p);
        canvas.drawRect(b.right - o, b.top, b.right, b.bottom, p);
    }

    private int colour(int x, int y, int v) {
        switch (type) {
            case STONE:
                return new int[]{0xFF8A8A8A, 0xFF7A7A7A, 0xFF969696}[v];
            case GOLD:
                return new int[]{0xFFE8B923, 0xFFD19F12, 0xFFF5D35A}[v];
            default:
                boolean grassRow = y < 4 || (y == 4 && v == 0);
                return grassRow
                        ? new int[]{0xFF5FA83A, 0xFF4E9230, 0xFF6DB848}[v]
                        : new int[]{0xFF7A4E24, 0xFF6B4320, 0xFF8B5A2B}[v];
        }
    }

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
