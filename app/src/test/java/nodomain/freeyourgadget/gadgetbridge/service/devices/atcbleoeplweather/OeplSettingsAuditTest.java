/*  Copyright (C) 2026 Gadgetbridge contributors

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.RectF;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardContent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.content.CardStyle;
import nodomain.freeyourgadget.gadgetbridge.service.devices.atcbleoeplweather.oepl.OeplCanvas;

/**
 * Every design, in every setting it can be put into, on every panel the catalogue knows.
 * <p>
 * {@link OeplLayoutAuditTest} sweeps the designs across the geometries with one setting each.
 * That is the sweep that was in place when a week of seven columns came out as a row of single
 * letters on a 400x300 panel - because the one week card it drew was set to three days, where
 * the columns are twice as wide. A design is not one layout: it is one per value its settings
 * can be set to, and the faults live in the corners of that table rather than in its middle.
 * <p>
 * So {@link OeplSettings} enumerates the values the settings screen really offers, and each of
 * them is drawn on every panel geometry and checked for the five faults that can be told apart
 * from a layout decision:
 * <ul>
 *     <li>ink outside the area the card was given,</li>
 *     <li>two pieces of text in the same place,</li>
 *     <li>text in the colour of the fill it sits on, which cannot be seen at all,</li>
 *     <li>text covered by a fill drawn after it, which is worse,</li>
 *     <li>a label cut down to nothing but an ellipsis, which carries no information.</li>
 * </ul>
 * Then there is the panel's own palette: a two colour tag has no accent, its accent
 * <em>is</em> the ink, and a card that tells two things apart by colour alone tells them apart
 * by nothing there. That is why the sweep runs over the styles as well.
 * <p>
 * The full report is written to app/build/oepl-gallery/settings-audit.txt.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE)
public class OeplSettingsAuditTest {

    /** How far outside its area a run may reach before it counts: anti aliasing, rounding. */
    private static final float SLACK = 1.0f;
    /** As in the layout audit: deliberately close to nothing. */
    private static final float OVERLAP_SHARE = 0.02f;
    private static final float OVERLAP_PIXELS = 2.0f;
    /** How much of a run has to be under a fill before it counts as covered by it. */
    private static final float COVERED_SHARE = 0.75f;

    /**
     * The panel colours a display can have. A two colour tag is the one that matters: there the
     * accent is the ink, so anything told apart by the accent alone is not told apart.
     */
    private static final int[][] STYLES = {
            {OEPLImageCodec.RED, 0}, {OEPLImageCodec.YELLOW, 0},
            {OEPLImageCodec.BLACK, 0}, {OEPLImageCodec.RED, 1},
    };

    /** The shapes the style sweep runs on, one of every family rather than all of them. */
    private static final int[][] FAMILIES = {{384, 184}, {296, 128}, {400, 300}, {800, 480},
            {152, 152}, {128, 296}, {960, 672}, {160, 80}};

    /**
     * Sizes the catalogue does not have.
     * <p>
     * A tag reports its own resolution, so the catalogue is a list of the ones seen so far
     * rather than a list of the ones that exist: a panel this project has never met is a
     * firmware update away. These are therefore not tag types but a grid across the whole
     * plausible range - from a panel smaller than any real tag to one larger than any -
     * together with the shapes that break layouts rather than stretch them: a letterbox, a
     * tower, a square, and sizes that are not round numbers.
     */
    private static int[][] beyondTheCatalogue() {
        final int[] edges = {SMALLEST_EDGE, 100, 160, 250, 400, 640, 1000, 1600};
        final List<int[]> shapes = new ArrayList<>();
        for (final int w : edges) {
            for (final int h : edges) {
                if (Math.max(w, h) <= Math.min(w, h) * WIDEST_RATIO) {
                    shapes.add(new int[]{w, h});
                }
            }
        }
        // and the shapes that are awkward rather than merely unfamiliar: panels larger than
        // any tag here, a letterbox and a tower at the edge of what is allowed for, a square,
        // and sizes that are not round numbers
        for (final int[] odd : new int[][]{{1872, 1404}, {1404, 1872}, {2560, 1440},
                {1360, 240}, {240, 1360}, {97, 211}, {211, 97}, {311, 457}, {457, 311},
                {1021, 631}, {631, 1021}, {480, 480}}) {
            shapes.add(odd);
        }
        return shapes.toArray(new int[0][]);
    }

    /**
     * The narrowest a panel is taken to be. Eighty pixels is the narrowest edge in the whole
     * catalogue, and a tag narrower than that is not a thing anybody builds: the panels come
     * off a handful of production lines in a handful of sizes. A card given sixty four pixels
     * of width has no room for a place name and a range at a size the panel can show, and
     * making it fit would cost the real tags their layout.
     */
    private static final int SMALLEST_EDGE = 80;

    /**
     * The most lopsided a panel is taken to be. The most extreme in the catalogue is 152 by
     * 522, a little over three to one, so six to one is twice as far as any tag goes - and a
     * strip twenty five times longer than it is tall is a LED ticker, not an e-paper tag.
     */
    private static final int WIDEST_RATIO = 6;

    @Before
    public void loadFonts() {
        OeplDemo.loadFonts();
    }

    /**
     * The whole table against the whole catalogue, in the ordinary three colour style. This is
     * the sweep: a few thousand cards, none of which anybody will ever look at.
     */
    @Test
    public void everySettingOfEveryDesignHoldsOnEveryPanel() throws Exception {
        final List<OeplSettings.Case> cases = OeplSettings.all();
        final List<int[]> shapes = OeplLayoutAuditTest.shapes();
        assertTrue("the settings should come to far more cases than there are designs",
                cases.size() > 120);

        final Report report = new Report("settings-audit.txt");
        report.out.printf("%d settings of %d designs x %d panel geometries%n", cases.size(),
                designs(cases), shapes.size());

        for (final int[] shape : shapes) {
            for (final OeplSettings.Case one : cases) {
                check(one, shape[0], shape[1], OEPLImageCodec.RED, false, report);
            }
        }
        report.finish();
        assertEquals(report.summary(), 0, report.problems.size());
    }

    /**
     * The same table again on one panel of every family, in every style a display can have:
     * the accent red, the accent yellow, no accent at all - and the temperatures in Fahrenheit,
     * which is three digits where Celsius has two.
     */
    @Test
    public void everySettingHoldsInEveryStyleAPanelCanHave() throws Exception {
        final List<OeplSettings.Case> cases = OeplSettings.all();
        final Report report = new Report("settings-audit-styles.txt");
        report.out.printf("%d settings x %d panels x %d styles%n", cases.size(), FAMILIES.length,
                STYLES.length);

        for (final int[] style : STYLES) {
            for (final int[] shape : FAMILIES) {
                for (final OeplSettings.Case one : cases) {
                    check(one, shape[0], shape[1], style[0], style[1] == 1, report);
                }
            }
        }
        report.finish();
        assertEquals(report.summary(), 0, report.problems.size());
    }

    /**
     * Every design, in every setting and against its own data, on sizes no tag in the
     * catalogue has.
     * <p>
     * The catalogue is what has been seen, not what exists: a display reports its own
     * resolution, and the next firmware brings panels nobody here has held. A layout that is
     * right because it was tried on 384 by 184 and on 296 by 128 is right by coincidence, so
     * it is tried on a grid from smaller than any tag to larger than any - and on the shapes
     * that are hostile rather than merely unfamiliar: a letterbox 1360 by 120, a tower 120 by
     * 1360, and sizes that are not round numbers.
     */
    @Test
    public void everyDesignHoldsOnPanelsTheCatalogueHasNever() throws Exception {
        final List<OeplSettings.Case> cases = new ArrayList<>(OeplSettings.all());
        cases.addAll(OeplSettings.deepCases());
        final int[][] shapes = beyondTheCatalogue();

        final Report report = new Report("settings-audit-sizes.txt");
        report.out.printf("%d cases x %d sizes the catalogue does not have%n", cases.size(),
                shapes.length);
        final StringBuilder sb = new StringBuilder();
        for (final int[] shape : shapes) {
            sb.append(shape[0]).append('x').append(shape[1]).append(' ');
        }
        report.out.println("sizes: " + sb.toString().trim());
        report.out.println();

        for (final int[] shape : shapes) {
            for (final OeplSettings.Case one : cases) {
                check(one, shape[0], shape[1], OEPLImageCodec.RED, false, report);
            }
        }
        report.finish();
        assertEquals(report.summary(), 0, report.problems.size());
    }

    /**
     * Every design in every language the app is translated into.
     * <p>
     * A card is laid out around the words it will hold, and the words change with the reader:
     * Monday is "luni" in Romanian and "segunda-feira" in Portuguese, three times as wide. The
     * weekday that fits a column in German is the one that does not fit it in Russian, and
     * Arabic, Persian and Burmese bring their own digits, so even a date stamp changes width.
     * Which day a week starts on changes too - Sunday in the United States, Monday here,
     * Saturday in much of the Arab world - and that is the first column of the month sheet.
     * <p>
     * So the designs are drawn again in each of the fifty languages that have a translation in
     * this repository. The bundled face carries Latin, Greek and Cyrillic itself and Android
     * falls back to its own fonts for the rest, so this is about the room the words need
     * rather than about whether they can be drawn at all.
     */
    @Test
    public void everyDesignHoldsInEveryLanguageTheAppIsTranslatedInto() throws Exception {
        final List<OeplSettings.Case> cases = oncePerDesign();
        final Locale before = Locale.getDefault();

        final Report report = new Report("settings-audit-languages.txt");
        report.out.printf("%d designs x %d languages x %d panels%n", cases.size(),
                LANGUAGES.length, LANGUAGE_SHAPES.length);
        try {
            for (final String tag : LANGUAGES) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                report.note = tag;
                for (final int[] shape : LANGUAGE_SHAPES) {
                    for (final OeplSettings.Case one : cases) {
                        check(one, shape[0], shape[1], OEPLImageCodec.RED, false, report);
                    }
                }
            }
        } finally {
            Locale.setDefault(before);
            report.note = "";
        }
        report.finish();
        assertEquals(report.summary(), 0, report.problems.size());
    }

    /**
     * One case of every design, plus the awkward one of every design that has it. The whole
     * settings table in fifty languages would be a quarter of a million cards; what changes
     * with the language is the width of the words, and that shows on one setting as well as
     * on all of them.
     */
    private static List<OeplSettings.Case> oncePerDesign() {
        final java.util.Set<String> plain = new java.util.LinkedHashSet<>();
        final java.util.Set<String> awkward = new java.util.LinkedHashSet<>();
        final List<OeplSettings.Case> cases = new ArrayList<>();
        for (final OeplSettings.Case one : OeplSettings.all()) {
            if (one.stress ? awkward.add(one.design) : plain.add(one.design)) {
                cases.add(one);
            }
        }
        return cases;
    }

    /** The languages this repository carries a translation for. */
    private static final String[] LANGUAGES = {"ar", "be", "bg", "ca", "cs", "de", "el",
            "en-GB", "en-US", "es", "et", "eu", "fa", "fi", "fr", "fr-CA", "gl", "he", "hi",
            "hr", "hu", "ia", "id", "it", "ja", "ka", "kn", "ko", "lt", "lv", "ml", "my",
            "nb-NO", "nl", "pl", "pt", "pt-BR", "pt-PT", "ro", "ru", "sk", "sr", "sv", "ta",
            "th", "tr", "uk", "vi", "zh-CN", "zh-TW"};

    /**
     * The panels the languages are swept on: the one the fault was reported from, the two
     * commonest tags, the small square where every word is too long, and a large panel.
     */
    private static final int[][] LANGUAGE_SHAPES = {{400, 300}, {384, 184}, {296, 128},
            {152, 152}, {800, 480}};

    /**
     * Nothing is left out. A design that is added to the settings list and to no table here
     * would be swept over every panel geometry by nobody, so the tables are checked against
     * the list of designs rather than against themselves.
     */
    @Test
    public void everyDesignTheSettingsOfferHasATableOfItsOwn() {
        final java.util.Set<String> settings = new java.util.LinkedHashSet<>();
        for (final OeplSettings.Case one : OeplSettings.all()) {
            settings.add(one.design);
        }
        final java.util.Set<String> quarters = new java.util.LinkedHashSet<>();
        for (final OeplSettings.Case one : OeplSettings.quadCases()) {
            final String design = one.settings.substring(one.settings.indexOf('=') + 2);
            quarters.add(design);
        }

        final List<String> missing = new ArrayList<>();
        final List<String> unquartered = new ArrayList<>();
        for (final String design : OeplDemo.IDS) {
            if (!settings.contains(design)) {
                missing.add(design);
            }
            if (!quarters.contains(design) && !design.equals("panel_quad")) {
                unquartered.add(design);
            }
        }
        assertEquals("designs with no settings table: " + missing, 0, missing.size());
        assertEquals("designs never put in a quarter: " + unquartered, 0, unquartered.size());
    }

    /**
     * Every remaining design against the whole of its own input: the weather from a winter
     * night to a heatwave, a diary from empty to twenty entries, a dashboard from one value to
     * sixteen, a QR code from a word to a paragraph, batteries full and empty.
     * <p>
     * The settings sweep asks what happens when a user changes a setting. This asks what
     * happens when the day does - which is the half of a layout nobody can set and every panel
     * eventually meets.
     */
    @Test
    public void everyDesignHoldsWhateverTheDayHandsIt() throws Exception {
        final List<OeplSettings.Case> cases = OeplSettings.deepCases();
        final List<int[]> shapes = OeplLayoutAuditTest.shapes();
        assertTrue("the data of a card is a table of its own", cases.size() > 180);

        final Report report = new Report("settings-audit-data.txt");
        report.out.printf("%d kinds of data over %d designs x %d panel geometries%n",
                cases.size(), designs(cases), shapes.size());
        for (final int[] shape : shapes) {
            for (final OeplSettings.Case one : cases) {
                check(one, shape[0], shape[1], OEPLImageCodec.RED, false, report);
            }
        }
        // and the two that turn three digit figures into a layout problem
        for (final int[] shape : FAMILIES) {
            for (final OeplSettings.Case one : cases) {
                check(one, shape[0], shape[1], OEPLImageCodec.BLACK, true, report);
            }
        }
        report.finish();
        assertEquals(report.summary(), 0, report.problems.size());
    }

    /**
     * Every design in every quarter of the quartered panel. A quarter is a panel of its own,
     * with an origin that is not the panel's, so this is where a card that lays itself out
     * from the panel rather than from the area it was handed shows it.
     */
    @Test
    public void everyDesignHoldsInEveryQuarterOfTheQuadPanel() throws Exception {
        final List<OeplSettings.Case> cases = OeplSettings.quadCases();
        assertTrue("every design belongs in every quarter", cases.size() > 150);

        final List<int[]> shapes = OeplLayoutAuditTest.shapes();
        final Report report = new Report("settings-audit-quad.txt");
        report.out.printf("%d quarters x %d panels%n", cases.size(), shapes.size());
        for (final int[] shape : shapes) {
            for (final OeplSettings.Case one : cases) {
                check(one, shape[0], shape[1], OEPLImageCodec.RED, false, report);
            }
        }
        report.finish();
        assertEquals(report.summary(), 0, report.problems.size());
    }

    /**
     * The picture card, over every setting it has: the three ways of reducing a photograph to
     * the colours a tag can show, the three ways of fitting it, the palettes of a two, three
     * and four colour panel, pictures whose shape fights the panel's, and the zoom and pan the
     * picker leaves behind.
     */
    @Test
    public void everyPictureSettingHoldsOnEveryPanel() throws Exception {
        final List<OeplSettings.Case> cases = OeplSettings.pictureCases();
        assertTrue("the picture settings make a table of their own", cases.size() > 40);

        final List<int[]> shapes = OeplLayoutAuditTest.shapes();
        final Report report = new Report("settings-audit-pictures.txt");
        report.out.printf("%d picture settings x %d panels%n", cases.size(), shapes.size());
        for (final int[] shape : shapes) {
            for (final OeplSettings.Case one : cases) {
                check(one, shape[0], shape[1], OEPLImageCodec.RED, false, report);
                check(one, shape[0], shape[1], OEPLImageCodec.BLACK, false, report);
            }
        }
        report.finish();
        assertEquals(report.summary(), 0, report.problems.size());
    }

    // ------------------------------------------------------------------ the checks

    private static void check(final OeplSettings.Case one, final int w, final int h,
                              final int accent, final boolean fahrenheit, final Report report) {
        final CardContent content = one.card();
        if (!content.hasData()) {
            return;
        }
        // an area that does not start at the origin, so a content that forgets to translate is
        // caught here rather than on a panel
        final float ox = 7f;
        final float oy = 5f;
        final Bitmap sheet = Bitmap.createBitmap((int) ox + w + 9, (int) oy + h + 9,
                Bitmap.Config.ARGB_8888);
        final OeplInkRecorder recorder = new OeplInkRecorder(sheet);
        recorder.drawColor(0xffffffff);
        final RectF area = new RectF(ox, oy, ox + w, oy + h);
        final CardStyle style = new CardStyle(OEPLImageCodec.BLACK, OEPLImageCodec.WHITE, accent,
                fahrenheit);
        content.draw(recorder, style, area);
        report.drawn++;

        final String where = (report.note.isEmpty() ? "" : report.note + "  ")
                + String.format("%-16s %-44s %4dx%-4d %-6s%s", one.design,
                one.settings, w, h, accent == OEPLImageCodec.BLACK ? "2col"
                        : (accent == OEPLImageCodec.YELLOW ? "yellow" : "red"),
                fahrenheit ? " F" : "");
        final List<OeplInkRecorder.Run> runs = recorder.runs();

        for (final OeplInkRecorder.Run run : runs) {
            final String side = outside(run.wanted, area);
            if (side != null) {
                report.add("ink outside the card", where + "  " + side + "  " + run);
            }
        }

        final List<OeplInkRecorder.Run> texts = recorder.textRuns();
        for (int i = 0; i < texts.size(); i++) {
            for (int j = i + 1; j < texts.size(); j++) {
                final float shared = overlap(texts.get(i).box, texts.get(j).box);
                final float smaller = Math.min(texts.get(i).area(), texts.get(j).area());
                if (shared >= OVERLAP_PIXELS && shared > smaller * OVERLAP_SHARE) {
                    report.add("text on top of text", String.format("%s  %.0f px  %s  <>  %s",
                            where, shared, texts.get(i), texts.get(j)));
                }
            }
        }

        // A piece of text is gone when it is the colour of what it sits on, and gone as well
        // when something is filled over it afterwards. Both are invisible to a check that only
        // looks at where the ink landed, and both are what a panel actually shows: nothing.
        for (int i = 0; i < runs.size(); i++) {
            final OeplInkRecorder.Run run = runs.get(i);
            if (!run.text || !run.visible()) {
                continue;
            }
            for (int j = 0; j < runs.size(); j++) {
                final OeplInkRecorder.Run fill = runs.get(j);
                if (i == j || fill.text || !fill.filled || !fill.visible()) {
                    continue;
                }
                if (overlap(run.box, fill.box) < run.area() * COVERED_SHARE) {
                    continue;
                }
                if (j < i && fill.color == run.color) {
                    report.add("text in the colour of its own background",
                            where + "  " + run + " on " + fill);
                } else if (j > i && fill.color != run.color) {
                    report.add("text filled over afterwards", where + "  " + run + " under "
                            + fill);
                }
            }
        }

        // Ink that is there and cannot be read. The panels here run at about five pixels to
        // the millimetre, so anything under OeplCanvas.READABLE is a line of letters that are
        // present and not read - and a card that produces one has shrunk something it should
        // have dropped or cut instead.
        for (final OeplInkRecorder.Run run : texts) {
            if (!run.icon && run.size > 0f && run.size < OeplCanvas.READABLE - 0.5f) {
                report.add("text too small for the panel to show",
                        where + "  " + run + String.format(" at %.1f px", run.size));
            }
        }

        // What is left of a label that had to be cut. A card sets its text smaller before it
        // cuts anything, so whatever is still cut here had no room at any size - but a label
        // cut down to a letter or two says nothing at all, and that is a layout fault rather
        // than a panel being small.
        for (final OeplInkRecorder.Run run : texts) {
            final String trimmed = run.what.trim();
            if (!trimmed.endsWith("…")) {
                continue;
            }
            report.cut++;
            report.cutIn(one.design + (one.stress ? " (long data)" : ""), run.what);
            final String kept = trimmed.substring(0, trimmed.length() - 1).trim();
            if (kept.isEmpty()) {
                report.add("a label cut down to nothing", where + "  " + run);
            } else if (kept.length() < 3 && run.box.width() > 10f) {
                report.add("a label cut down to a letter or two", where + "  " + run);
            }
        }

        sheet.recycle();
    }

    /** Which edge a box crosses, or null when it stays inside. */
    private static String outside(final RectF box, final RectF area) {
        final StringBuilder sb = new StringBuilder();
        if (box.left < area.left - SLACK) {
            sb.append("left by ").append(Math.round(area.left - box.left)).append(' ');
        }
        if (box.top < area.top - SLACK) {
            sb.append("top by ").append(Math.round(area.top - box.top)).append(' ');
        }
        if (box.right > area.right + SLACK) {
            sb.append("right by ").append(Math.round(box.right - area.right)).append(' ');
        }
        if (box.bottom > area.bottom + SLACK) {
            sb.append("bottom by ").append(Math.round(box.bottom - area.bottom)).append(' ');
        }
        return sb.length() == 0 ? null : sb.toString().trim();
    }

    private static float overlap(final RectF a, final RectF b) {
        final float w = Math.min(a.right, b.right) - Math.max(a.left, b.left);
        final float h = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        return w <= 0f || h <= 0f ? 0f : w * h;
    }

    private static int designs(final List<OeplSettings.Case> cases) {
        final java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (final OeplSettings.Case one : cases) {
            ids.add(one.design);
        }
        return ids.size();
    }

    // ------------------------------------------------------------------ the report

    /** What was found, written out in full and summarised for the failure message. */
    private static class Report {
        final List<String> problems = new ArrayList<>();
        final java.util.Map<String, Integer> kinds = new java.util.LinkedHashMap<>();
        final PrintWriter out;
        int drawn;
        /** What the sweep is varying beyond the case itself - a language, say. */
        String note = "";
        /** How many labels were cut at all, which is the number to watch rather than to fail on. */
        int cut;
        /** Which designs do the cutting, and the shortest thing each of them left behind. */
        final java.util.Map<String, Integer> cuts = new java.util.TreeMap<>();
        final java.util.Map<String, String> worst = new java.util.TreeMap<>();

        void cutIn(final String design, final String what) {
            cuts.merge(design, 1, Integer::sum);
            final String before = worst.get(design);
            if (before == null || what.length() < before.length()) {
                worst.put(design, what);
            }
        }

        Report(final String name) throws Exception {
            final File dir = new File("build/oepl-gallery");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            out = new PrintWriter(new File(dir, name), "UTF-8");
        }

        void add(final String kind, final String line) {
            problems.add(kind + ": " + line);
            kinds.merge(kind, 1, Integer::sum);
        }

        void finish() {
            out.printf("%d cards drawn, %d problems, %d labels cut%n%n", drawn, problems.size(),
                    cut);
            for (final java.util.Map.Entry<String, Integer> kind : kinds.entrySet()) {
                out.printf("%6d x %s%n", kind.getValue(), kind.getKey());
            }
            out.println();
            // Cutting is not a fault in itself - a title that fills a notice board has to be
            // cut on a price tag - but a design that does a lot of it is a design to look at,
            // so the tally goes into the report even when nothing failed.
            out.printf("labels cut, by design (every card sets its text smaller first):%n");
            for (final java.util.Map.Entry<String, Integer> entry : cuts.entrySet()) {
                out.printf("%6d x %-18s shortest: \"%s\"%n", entry.getValue(), entry.getKey(),
                        worst.get(entry.getKey()));
            }
            out.println();
            for (final String problem : problems) {
                out.println("  " + problem);
            }
            out.close();
        }

        String summary() {
            final StringBuilder sb = new StringBuilder(problems.size()
                    + " problems in " + drawn + " cards:\n");
            for (final java.util.Map.Entry<String, Integer> kind : kinds.entrySet()) {
                sb.append("  ").append(kind.getValue()).append(" x ").append(kind.getKey())
                        .append('\n');
            }
            for (int i = 0; i < Math.min(30, problems.size()); i++) {
                sb.append("  ").append(problems.get(i)).append('\n');
            }
            if (problems.size() > 30) {
                sb.append("  ... and ").append(problems.size() - 30)
                        .append(" more, see app/build/oepl-gallery/settings-audit.txt\n");
            }
            return sb.toString();
        }
    }
}
