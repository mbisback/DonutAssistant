package dev.donutassistant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DonutAssistantClient implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern IN = Pattern.compile("(?i)(\\S+)\\s+paid\\s+you\\s+\\$?\\s*([0-9][0-9,.]*\\s*[kmbt]?)");
    private static final Pattern OUT = Pattern.compile("(?i)You\\s+paid\\s+(\\S+)\\s+\\$?\\s*([0-9][0-9,.]*\\s*[kmbt]?)");
    private static final Pattern DM = Pattern.compile("(?i)(whispers?|tells you|msg from/to|->me|\\[MSG\\]|^(From|To)\\s+\\S+:)");
    private static final Deque<Tx> LIVE = new ArrayDeque<>();
    private static final List<Tx> ALL = new ArrayList<>();
    private static final List<TextCard> TEXT = new ArrayList<>();
    private static final Path DIR = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
    private static final Path DATA = DIR.resolve("donut-assistant.json");
    private static KeyMapping openKey, trackKey, hudKey;
    private static boolean tracking = false, hudVisible = true;
    private static long incoming, outgoing;
    private static int accent = 0xFF8B5CF6;
    private static String draft = "Donut Assistant\nTransaction notes";
    private static int selectedTab = 0;

    @Override public void onInitializeClient() {
        load();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.donut_assistant.open", GLFW.GLFW_KEY_RIGHT_SHIFT, "key.category.donut_assistant"));
        trackKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.donut_assistant.tracking", GLFW.GLFW_KEY_UNKNOWN, "key.category.donut_assistant"));
        hudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.donut_assistant.hud", GLFW.GLFW_KEY_UNKNOWN, "key.category.donut_assistant"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) client.setScreen(new AssistantScreen());
            while (trackKey.consumeClick()) { tracking = !tracking; save(); }
            while (hudKey.consumeClick()) { hudVisible = !hudVisible; save(); }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> parse(message.getString()));
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> renderHud(graphics));
    }

    private static void parse(String line) {
        if (!tracking || DM.matcher(line).find()) return;
        Matcher m = IN.matcher(line);
        boolean in = m.find();
        if (!in) { m = OUT.matcher(line); if (!m.find()) return; }
        String player = m.group(1).replaceAll("[^A-Za-z0-9_.]", "");
        long amount = AmountFormat.parse(m.group(2));
        if (player.isBlank() || amount <= 0) return;
        Tx tx = new Tx(System.currentTimeMillis(), player, amount, in);
        LIVE.addFirst(tx); while (LIVE.size() > 100) LIVE.removeLast();
        ALL.add(tx);
        if (in) incoming = safeAdd(incoming, amount); else outgoing = safeAdd(outgoing, amount);
        save();
    }

    private static long safeAdd(long a, long b) { try { return Math.addExact(a,b); } catch (ArithmeticException e) { return Long.MAX_VALUE; } }
    private static void renderHud(GuiGraphics g) {
        if (!hudVisible) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String title = "DONUT ASSISTANT" + (tracking ? "  • TRACKING" : "  • PAUSED");
        int width = Math.max(190, mc.font.width(title) + 18);
        g.fill(10, 10, 10 + width, 66, 0xCC11131B);
        g.fill(10, 10, 13, 66, accent);
        g.drawString(mc.font, title, 20, 18, 0xFFFFFFFF, false);
        g.drawString(mc.font, "IN  " + AmountFormat.pretty(incoming), 20, 34, 0xFF74E0A0, false);
        g.drawString(mc.font, "OUT " + AmountFormat.pretty(outgoing), 110, 34, 0xFFFF9B9B, false);
        g.drawString(mc.font, "NET " + AmountFormat.signed(incoming - outgoing), 20, 49, 0xFFE7E9F2, false);
    }

    private static void load() {
        try {
            Files.createDirectories(DIR);
            if (Files.exists(DATA)) try (Reader r = Files.newBufferedReader(DATA)) {
                SaveData d = GSON.fromJson(r, SaveData.class);
                if (d != null) {
                    tracking = d.tracking; hudVisible = d.hudVisible; accent = d.accent;
                    incoming = d.incoming; outgoing = d.outgoing;
                    if (d.transactions != null) { ALL.addAll(d.transactions); for (Tx tx : d.transactions) { LIVE.addFirst(tx); if (LIVE.size() > 100) LIVE.removeLast(); } }
                    if (d.textCards != null) TEXT.addAll(d.textCards);
                    if (d.draft != null) draft = d.draft;
                }
            }
            if (TEXT.isEmpty()) TEXT.add(new TextCard("Welcome", "Donut Assistant\nAdd your own multiline text here", 20, 85, 1.0, 0xFFFFFFFF, 0xBB11131B, true));
        } catch (Exception e) { System.err.println("[Donut Assistant] Config load failed: " + e.getMessage()); }
    }
    static void save() {
        try {
            Files.createDirectories(DIR);
            SaveData d = new SaveData(); d.tracking=tracking; d.hudVisible=hudVisible; d.accent=accent; d.incoming=incoming; d.outgoing=outgoing;
            d.transactions = new ArrayList<>(ALL); if (d.transactions.size() > 5000) d.transactions = d.transactions.subList(d.transactions.size()-5000, d.transactions.size());
            d.textCards = new ArrayList<>(TEXT); d.draft = draft;
            try (Writer w = Files.newBufferedWriter(DATA)) { GSON.toJson(d,w); }
        } catch (Exception e) { System.err.println("[Donut Assistant] Config save failed: " + e.getMessage()); }
    }

    public static final class AmountFormat {
        public static long parse(String raw) {
            try {
                String s = raw.replace(",", "").replaceAll("\\s+", "").toUpperCase();
                double mult = 1;
                if (s.endsWith("K")) { mult=1_000; s=s.substring(0,s.length()-1); }
                else if (s.endsWith("M")) { mult=1_000_000; s=s.substring(0,s.length()-1); }
                else if (s.endsWith("B")) { mult=1_000_000_000; s=s.substring(0,s.length()-1); }
                else if (s.endsWith("T")) { mult=1_000_000_000_000d; s=s.substring(0,s.length()-1); }
                double v=Double.parseDouble(s)*mult;
                if (!Double.isFinite(v) || v<0 || v>Long.MAX_VALUE) return 0;
                return Math.round(v);
            } catch (Exception e) { return 0; }
        }
        public static String pretty(long n) {
            double a=Math.abs((double)n);
            if (a>=1e12) return String.format("%.1fT", n/1e12);
            if (a>=1e9) return String.format("%.1fB", n/1e9);
            if (a>=1e6) return String.format("%.1fM", n/1e6);
            if (a>=1e3) return String.format("%.1fK", n/1e3);
            return Long.toString(n);
        }
        public static String signed(long n) { return (n>=0?"+":"-")+pretty(Math.abs(n)); }
    }
    record Tx(long timestamp, String player, long amount, boolean incoming) {}
    static class TextCard {
        String name, text; int x,y,foreground,background; double scale; boolean gradient;
        TextCard(String name,String text,int x,int y,double scale,int foreground,int background,boolean gradient) {
            this.name=name; this.text=text; this.x=x; this.y=y; this.scale=scale; this.foreground=foreground; this.background=background; this.gradient=gradient;
        }
    }
    static class SaveData {
        boolean tracking, hudVisible=true; int accent=0xFF8B5CF6; long incoming,outgoing;
        List<Tx> transactions=new ArrayList<>(); List<TextCard> textCards=new ArrayList<>(); String draft;
    }

    static class AssistantScreen extends net.minecraft.client.gui.screens.Screen {
        private String textBuffer = draft;
        private String titleBuffer = "Custom text";
        private int tab=0;
        private boolean editing=false;
        private AssistantScreen() { super(Component.literal("Donut Assistant")); }
        @Override protected void init() { }
        @Override public void render(GuiGraphics g, int mx, int my, float delta) {
            int w=width,h=height;
            g.fill(0,0,w,h,0xB9000000);
            g.fill(w/2-210,h/2-145,w/2+210,h/2+145,0xF0121420);
            g.fill(w/2-210,h/2-145,w/2+210,h/2-112,accent);
            g.drawString(font,"DONUT ASSISTANT",w/2-195,h/2-133,0xFFFFFFFF,false);
            String[] tabs={"OVERVIEW","TRANSACTIONS","TEXT HUD","SETTINGS"};
            for(int i=0;i<tabs.length;i++){
                int x=w/2-195+i*98;
                g.fill(x,h/2-102,x+92,h/2-78,tab==i?accent:0xFF272B39);
                g.drawString(font,tabs[i],x+7,h/2-94,0xFFFFFFFF,false);
            }
            if(tab==0){
                g.drawString(font,"Transaction tracking",w/2-190,h/2-58,0xFFFFFFFF,false);
                g.drawString(font,"Status: "+(tracking?"ON":"OFF")+"  (toggle with keybind)",w/2-190,h/2-37,0xFFBFC5D6,false);
                g.drawString(font,"Incoming: "+AmountFormat.pretty(incoming),w/2-190,h/2-10,0xFF74E0A0,false);
                g.drawString(font,"Outgoing: "+AmountFormat.pretty(outgoing),w/2-190,h/2+12,0xFFFF9B9B,false);
                g.drawString(font,"Net: "+AmountFormat.signed(incoming-outgoing),w/2-190,h/2+34,0xFFFFFFFF,false);
                g.drawString(font,"Transactions stored: "+ALL.size(),w/2-190,h/2+60,0xFFBFC5D6,false);
            } else if(tab==1) {
                g.drawString(font,"RECENT TRANSACTIONS (latest 100)",w/2-190,h/2-58,0xFFFFFFFF,false);
                int y=h/2-38;
                for(Tx tx:LIVE){ if(y>h/2+120) break; g.drawString(font,(tx.incoming?"+ ":"- ")+tx.player+"  "+AmountFormat.pretty(tx.amount),w/2-190,y,tx.incoming?0xFF74E0A0:0xFFFF9B9B,false); y+=13; }
            } else if(tab==2) {
                g.drawString(font,"MULTILINE TEXT HUD",w/2-190,h/2-58,0xFFFFFFFF,false);
                g.drawString(font,"Click to add/edit a text card. Lines use \n.",w/2-190,h/2-37,0xFFBFC5D6,false);
                int y=h/2-10;
                for(TextCard c:TEXT){g.fill(w/2-190,y-3,w/2+190,y+30,c.background);g.drawString(font,c.name,w/2-180,y,0xFFFFFFFF,false);g.drawString(font,c.text.replace("\n"," / "),w/2-180,y+13,c.foreground,false);y+=38;}
                g.drawString(font,"N: add sample multiline card   G: toggle gradient",w/2-190,h/2+112,0xFFBFC5D6,false);
            } else {
                g.drawString(font,"CONFIGURATION",w/2-190,h/2-58,0xFFFFFFFF,false);
                g.drawString(font,"Right Shift opens this window",w/2-190,h/2-32,0xFFBFC5D6,false);
                g.drawString(font,"F7 toggles the text/summary HUD",w/2-190,h/2-12,0xFFBFC5D6,false);
                g.drawString(font,"Tracking only records matching public chat lines.",w/2-190,h/2+12,0xFFBFC5D6,false);
                g.drawString(font,"Data file: config/donut-assistant.json",w/2-190,h/2+38,0xFFBFC5D6,false);
            }
            g.drawString(font,"ESC to close  •  1-4 switch tabs  •  N add text",w/2-195,h/2+126,0xFF9AA3B8,false);
        }
        @Override public boolean keyPressed(int key,int scan,int mods) {
            if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}
            if(key>=GLFW.GLFW_KEY_1&&key<=GLFW.GLFW_KEY_4){tab=key-GLFW.GLFW_KEY_1;return true;}
            if(tab==2&&key==GLFW.GLFW_KEY_N){
                TEXT.add(new TextCard("Text "+(TEXT.size()+1),"New line one\nNew line two",30,100+TEXT.size()*45,1.0,0xFFFFFFFF,0xBB11131B,true));save();return true;
            }
            if(tab==2&&key==GLFW.GLFW_KEY_G){ for(TextCard c:TEXT)c.gradient=!c.gradient;save();return true; }
            return super.keyPressed(key,scan,mods);
        }
        @Override public boolean mouseClicked(double mx,double my,int button) {
            int left=width/2-195, top=height/2-102;
            for(int i=0;i<4;i++){int x=left+i*98;if(mx>=x&&mx<=x+92&&my>=top&&my<=top+24){tab=i;return true;}}
            if(tab==0 && mx>width/2-195&&mx<width/2+195&&my>height/2-60&&my<height/2-20){tracking=!tracking;save();return true;}
            return super.mouseClicked(mx,my,button);
        }
        @Override public void onClose(){save();Minecraft.getInstance().setScreen(null);}
        @Override public boolean isPauseScreen(){return false;}
    }
}