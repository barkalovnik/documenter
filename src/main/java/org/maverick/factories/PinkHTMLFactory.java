package org.maverick.factories;

public class PinkHTMLFactory extends StandardHTMLFactory {
    /** Вторая css-стратегия - пошлые пастельные цвета: оранжевый, зеленый, розовый */
    @Override
    protected String css() {
        return """
                body{font-family:sans-serif;margin:24px;background:#FFCB73;color:#7D0057;}
                h1{font-size:22px;} h2{font-size:18px;margin:0 0 10px;} h3{font-size:14px;margin:16px 0 6px;color:#374151;}
                .card{background:#FFB840;border:1px solid #BF8A30;border-radius:0px;padding:16px 18px;margin:14px 0;}
                table{border-collapse:collapse;width:100%;font-size:13px;margin-bottom:6px;}
                td,th{border:1px solid #BF8A30;padding:5px 8px;text-align:left;vertical-align:top;}
                table.grid th{background:#E065BB;}
                .name{font-family:Consolas,monospace;}
                .meta{color:#912470;font-size:12px;}
                .descr{margin:0 0 10px;font-style:italic;color:#374151;}
                .kind{color:#912470;font-size:12px;}
                .badge{background:#91B52D;color:#fff;font-size:11px;border-radius:0px;padding:2px 6px;}
                .toc{columns:2;font-size:13px;} a{color:#739D00;text-decoration:none;} a:hover{text-decoration:underline;}
                code{background:#eef1f5;border-radius:3px;padding:1px 4px;font-size:12px;}
                """;
    }
}
