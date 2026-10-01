import net.dankito.readability4j.Readability4J;
import net.dankito.readability4j.Article;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * JVM 端冒烟测试：验证 Readability4J + jsoup(1.16.2) 在运行期能正常协作抽取正文。
 * （Android 上跑不了时，用它来确认库的兼容性与抽取效果）
 * 用法：java ExtractTest [url]
 */
public class ExtractTest {

    public static void main(String[] args) throws Exception {
        // 1) 内置样例（确定性）
        System.out.println("================ 样例 HTML ================");
        run("<内置样例>", SAMPLE);

        // 2) 真实网页（可选）
        if (args.length > 0) {
            System.out.println("\n================ 真实网页: " + args[0] + " ================");
            try {
                String html = fetch(args[0]);
                System.out.println("HTML 长度 = " + html.length());
                run(args[0], html);
            } catch (Exception e) {
                System.out.println("抓取失败(可能在墙外/超时)：" + e);
            }
        }
    }

    static void run(String url, String html) {
        try {
            Article a = new Readability4J(url, html).parse();
            String content = a.getContent();
            String plain = toPlainText(content, url);
            System.out.println("title        = " + a.getTitle());
            System.out.println("contentHtml  = " + (content == null ? -1 : content.length()) + " chars");
            System.out.println("正文纯文本    = " + plain.length() + " chars");
            System.out.println("--- 正文预览 ---");
            System.out.println(plain.substring(0, Math.min(500, plain.length())));
            System.out.println("-------- 判定: " + (plain.length() >= 150 ? "✅ 抽取成功" : "❌ 太短") + " --------");
        } catch (Throwable t) {
            System.out.println("❌ 抽取抛异常: " + t);
        }
    }

    static String toPlainText(String html, String baseUri) {
        Document doc = Jsoup.parse(html == null ? "" : html, baseUri);
        StringBuilder sb = new StringBuilder();
        for (Element el : doc.select("h1,h2,h3,h4,p,li,blockquote,pre,figcaption")) {
            // 注意：jsoup 的 el.select() 会包含元素自身，必须只看「子元素」里有没有块级标签
            if (hasBlockChild(el)) continue;
            String t = el.text().trim();
            if (t.isEmpty()) continue;
            String tag = el.tagName().toLowerCase();
            if (tag.equals("li")) sb.append("· ").append(t).append('\n');
            else if (tag.startsWith("h")) sb.append('\n').append(t).append("\n\n");
            else sb.append(t).append("\n\n");
        }
        return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    static boolean hasBlockChild(Element el) {
        for (Element c : el.children()) {
            String tn = c.tagName().toLowerCase();
            if (tn.equals("p") || tn.equals("li") || tn.equals("blockquote") || tn.equals("pre")) return true;
            if (tn.length() == 2 && tn.charAt(0) == 'h' && tn.charAt(1) >= '1' && tn.charAt(1) <= '6') return true;
        }
        return false;
    }

    static String fetch(String url) throws Exception {
        HttpClient c = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .timeout(Duration.ofSeconds(25)).build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("HTTP " + resp.statusCode());
        return resp.body();
    }

    static final String SAMPLE = "<!DOCTYPE html><html><head><meta charset='utf-8'><title>测试文章标题</title></head><body>"
            + "<header><nav><a href='/'>首页</a><a href='/news'>新闻</a></nav></header>"
            + "<div class='ad'>广告：买买买！</div>"
            + "<article><h1>量子计算取得重大突破</h1>"
            + "<p class='byline'>作者：张三 · 2026-09-27</p>"
            + "<p>科学家今天宣布，在纠错量子比特方面取得了里程碑式进展，这一成果有望让实用化量子计算机更快到来。</p>"
            + "<p>研究团队表示，新的拓扑编码方案把逻辑量子比特的错误率降低了一个数量级，这是过去十年中最重要的一步。</p>"
            + "<h2>为什么重要</h2>"
            + "<p>量子计算机容易受环境噪声干扰，纠错一直是最大瓶颈。此次突破让大规模量子计算更具可行性。</p>"
            + "<p>业内评论认为，未来五年内我们可能看到首批具备商业价值的量子算法落地。</p></article>"
            + "<footer><p>版权所有 © 2026</p></footer>"
            + "<div class='comments'><p>评论：太厉害了</p></div></body></html>";
}
