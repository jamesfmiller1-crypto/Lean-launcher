package nl.home.leanlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * The animated pixel world behind the home screen: sky, sun/moon, clouds, hills, trees
 * and four original creatures (Pebble, Flits, Bloop, Glowbugs).
 * Drawn at 192x108 pixels and scaled up without smoothing for a crisp pixel look.
 */
public class WorldView extends View {

    private static final int W = 192, H = 108;

    private final Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private final Canvas c = new Canvas(bmp);
    private final Paint p = new Paint();
    private final Paint scaler = new Paint();
    private final Rect src = new Rect(0, 0, W, H);
    private final Rect dst = new Rect();
    private final Random rnd = new Random(7);

    private final int[] far = new int[W];
    private final int[] hill = new int[W];
    private final int[][] spec = new int[260][2];
    private final int[][] stars = new int[40][2];
    private final int[] trees = {22, 61, 104, 141, 176};
    private final float[][] clouds = {{20, 14, 22}, {90, 8, 30}, {150, 20, 18}};
    private final float[][] bugs = new float[12][3];

    private long t = 0;
    private float pebbleX = 40;
    private int pebbleDir = 1;
    private int sit = 0;
    private boolean birds = false;
    private float birdX;
    private int birdY;
    private int birdWait = 60;
    private boolean night = false;
    private boolean running = false;
    private int weather = 0; // 0 clear, 1 cloudy, 2 rain, 3 snow
    private final float[][] drops = new float[48][2];

    public WorldView(Context ctx) {
        super(ctx);
        p.setAntiAlias(false);
        scaler.setFilterBitmap(false);
        scaler.setAntiAlias(false);
        for (int x = 0; x < W; x++) {
            far[x] = 48 + Math.round((float) (8 * Math.sin(x / 17.0) + 5 * Math.sin(x / 7.0 + 1)));
            hill[x] = 66 + Math.round((float) (5 * Math.sin(x / 23.0 + 2) + 3 * Math.sin(x / 9.0)));
        }
        for (int[] s : spec) { s[0] = rnd.nextInt(W); s[1] = rnd.nextInt(H); }
        for (int[] s : stars) { s[0] = rnd.nextInt(W); s[1] = rnd.nextInt(45); }
        for (float[] b : bugs) { b[0] = rnd.nextFloat() * W; b[1] = 50 + rnd.nextFloat() * 18; b[2] = rnd.nextFloat() * 6; }
        for (float[] d : drops) { d[0] = rnd.nextInt(W + 20); d[1] = rnd.nextInt(H); }
        updateNight();
    }

    /** 0 clear, 1 cloudy, 2 rain, 3 snow. The world follows the real weather. */
    public void setWeather(int kind) {
        weather = kind;
    }

    /** Called when the user moves to another tile: Pebble sits down for a moment. */
    public void onSelect() {
        sit = 90;
    }

    public void setRunning(boolean run) {
        running = run;
        if (run) invalidate();
    }

    private void updateNight() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        night = h >= 20 || h < 7;
    }

    private void px(float x, float y, int w, int h, int col) {
        if (w <= 0 || h <= 0) return;
        p.setColor(col);
        int ix = Math.round(x), iy = Math.round(y);
        c.drawRect(ix, iy, ix + w, iy + h, p);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        step();
        dst.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(bmp, src, dst, scaler);
        if (running) postInvalidateDelayed(33); // ~30 frames per second
    }

    private void step() {
        t++;
        if (t % 300 == 0) updateNight();

        // Sky
        int[] sky = night
                ? new int[]{0xFF0B1530, 0xFF13204A, 0xFF1C2C5E, 0xFF24366E}
                : new int[]{0xFF5BA8E0, 0xFF6FB6E8, 0xFF86C4EE, 0xFFA0D2F2};
        if (weather >= 1) {
            sky = night
                    ? new int[]{0xFF10141C, 0xFF171C26, 0xFF1E2430, 0xFF262D3A}
                    : new int[]{0xFF8A9AA8, 0xFF97A6B3, 0xFFA5B2BD, 0xFFB4BFC8};
        }
        for (int i = 0; i < 4; i++) px(0, i * 14, W, 14, sky[i]);
        px(0, 56, W, 52, sky[3]);

        // Sun or moon and stars
        if (weather != 0) {
            // overcast: no sun, moon or stars
        } else if (night) {
            for (int[] s : stars) if (rnd.nextFloat() > 0.03f) px(s[0], s[1], 1, 1, 0xFFFFFFFF);
            px(150, 10, 10, 10, 0xFFE8E8D0);
            px(152, 12, 3, 3, 0xFFC8C8B0);
        } else {
            px(150, 8, 14, 14, 0xFFFFE14D);
            px(152, 10, 10, 10, 0xFFFFF3A0);
        }

        // Clouds
        int cloud = night ? 0xFFCFD6E6 : 0xFFFFFFFF;
        if (weather >= 1) cloud = night ? 0xFF5A6478 : 0xFFD5DAE0;
        for (float[] k : clouds) {
            k[0] += 0.08f;
            if (k[0] > W) k[0] = -k[2];
            px(k[0], k[1], (int) k[2], 4, cloud);
            px(k[0] + 4, k[1] - 3, (int) k[2] - 8, 3, cloud);
        }

        if (weather >= 1) {
            for (int i = 0; i < 4; i++) {
                float cx = ((t * 0.05f + i * 58) % (W + 40)) - 40;
                px(cx, 5 + i * 7, 34, 5, cloud);
                px(cx + 6, 2 + i * 7, 20, 3, cloud);
            }
        }

        // Flits: a small flock of birds now and then
        if (!birds && --birdWait <= 0) {
            birds = true;
            birdX = -20;
            birdY = 16 + rnd.nextInt(18);
        }
        if (birds) {
            birdX += 0.6f;
            boolean flap = (t / 8) % 2 == 1;
            int col = night ? 0xFF9AA3B8 : 0xFF2C2C2A;
            int[][] flock = {{0, 0}, {-5, 3}, {-5, -3}, {-10, 6}};
            for (int[] o : flock) {
                float bx = birdX + o[0], by = birdY + o[1];
                px(bx, by, 1, 1, col);
                px(bx - 1, by - (flap ? 1 : 0), 1, 1, col);
                px(bx + 1, by - (flap ? 1 : 0), 1, 1, col);
            }
            if (birdX > W + 15) {
                birds = false;
                birdWait = 300 + rnd.nextInt(300);
            }
        }

        // Mountains, hills, dirt specks
        int mountain = night ? 0xFF2A3550 : 0xFF7A8FA8;
        int grass = night ? 0xFF2E5A22 : 0xFF5FA83A;
        int dirt = night ? 0xFF3A2614 : 0xFF7A4E24;
        int speck = night ? 0xFF2A1A0C : 0xFF5C3A19;
        for (int x = 0; x < W; x++) px(x, far[x], 1, H - far[x], mountain);
        for (int x = 0; x < W; x++) {
            px(x, hill[x], 1, 3, grass);
            px(x, hill[x] + 3, 1, H - hill[x] - 3, dirt);
        }
        for (int[] s : spec) if (s[1] > hill[s[0]] + 4) px(s[0], s[1], 1, 1, speck);

        // Bloop: peeks out from behind the middle tree
        long cyc = t % 600;
        float peek = 0;
        if (cyc > 300 && cyc < 520) peek = Math.min(6, Math.min((cyc - 300) / 6f, (520 - cyc) / 6f));
        if (peek > 0) {
            int bt = 104;
            float bx = bt - 1 + peek, by = hill[bt] - 8;
            int bc = night ? 0xFF5A4A9A : 0xFF7F77DD;
            px(bx, by, 7, 7, bc);
            px(bx + 1, by - 1, 5, 1, bc);
            px(bx + 2, by + 1, 3, 3, 0xFFFFFFFF);
            int look = (int) ((t / 40) % 2);
            px(bx + 3 + look, by + 2, 1, 1, 0xFF111111);
        }

        // Trees
        int trunk = night ? 0xFF3A2614 : 0xFF6B4423;
        int leaves = night ? 0xFF1F4418 : 0xFF3E8E2A;
        for (int tr : trees) {
            int h = hill[tr];
            px(tr, h - 10, 2, 10, trunk);
            px(tr - 4, h - 17, 10, 7, leaves);
            px(tr - 2, h - 20, 6, 3, leaves);
        }

        // Pebble: a little stone golem that walks, and sits when you pick something
        if (sit > 0) {
            sit--;
        } else {
            pebbleX += 0.15f * pebbleDir;
            if (pebbleX > 90 || pebbleX < 10) pebbleDir *= -1;
        }
        int gx = Math.round(pebbleX), gy = hill[gx];
        boolean sitting = sit > 0;
        int bodyH = sitting ? 5 : 6;
        int stone = night ? 0xFF6B6F78 : 0xFF9A9A9A;
        int dark = night ? 0xFF4A4D55 : 0xFF7A7A7A;
        if (!sitting) {
            boolean stepL = (t / 10) % 2 == 1;
            px(gx + 1, gy - 2, 2, 2, dark);
            px(gx + 4, gy - 2, 2, 2, dark);
            if (stepL) px(gx + 1, gy - 1, 2, 1, stone);
            else px(gx + 4, gy - 1, 2, 1, stone);
        }
        int top = gy - 2 - bodyH + (sitting ? 2 : 0);
        px(gx, top, 7, bodyH, stone);
        px(gx + 1, top - 3, 5, 3, stone);
        px(gx + 5, top + 1, 1, 1, dark);
        px(gx + 1, top + 2, 1, 1, dark);
        int ex = pebbleDir > 0 ? 4 : 2;
        boolean blink = t % 180 < 6;
        if (!blink) {
            px(gx + ex - 1, top - 2, 1, 1, 0xFF222222);
            px(gx + ex + 1, top - 2, 1, 1, 0xFF222222);
        }
        if (sitting) px(gx + 2, top - 6, 3, 1, 0xFFFFE14D);

        // Glowbugs: only at night
        if (night) {
            for (float[] b : bugs) {
                b[0] += (float) Math.sin(t / 30.0 + b[2]) * 0.2f;
                b[1] += (float) Math.cos(t / 25.0 + b[2]) * 0.12f;
                if (Math.sin(t / 12.0 + b[2] * 3) > 0) {
                    px(b[0] - 1, b[1] - 1, 3, 3, 0x59E6FF7A);
                    px(b[0], b[1], 1, 1, 0xFFE6FF7A);
                }
            }
        }

        // Rain or snow, following the real weather
        if (weather == 2) {
            for (float[] d : drops) {
                d[1] += 2.2f;
                d[0] -= 0.5f;
                if (d[1] > H) { d[1] = 0; d[0] = rnd.nextInt(W + 20); }
                px(d[0], d[1], 1, 3, 0xAA9CC4E8);
            }
        } else if (weather == 3) {
            for (float[] d : drops) {
                d[1] += 0.35f;
                d[0] += (float) Math.sin(t / 20.0 + d[1] / 9.0) * 0.2f;
                if (d[1] > H) { d[1] = 0; d[0] = rnd.nextInt(W + 20); }
                px(d[0], d[1], 1, 1, 0xFFFFFFFF);
            }
        }
    }
}
