package fr.renblood.medievalcoins.bank;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded diagnostics; no URLs, player identities or per-tick polling. */
public final class EconomyTimings {
    private static final long[] BOUNDS={1,4,8,16,32,64,250,1000,Long.MAX_VALUE};
    private static final Sample API=new Sample(),SAVE=new Sample();
    private static final Sample COUNTER_CACHE = new Sample(), STOCK_BATCH = new Sample();
    private static final class Sample {
        final LongAdder count=new LongAdder(),micros=new LongAdder();final AtomicLong max=new AtomicLong();
        final LongAdder[] buckets=java.util.stream.IntStream.range(0,BOUNDS.length).mapToObj(i->new LongAdder()).toArray(LongAdder[]::new);
        void add(long start){long elapsed=Math.max(0,System.nanoTime()-start);count.increment();micros.add(elapsed/1000);max.accumulateAndGet(elapsed/1000000,Math::max);for(int i=0;i<BOUNDS.length;i++)if(elapsed/1000000<=BOUNDS[i]){buckets[i].increment();break;}}
        String text(){long total=count.sum();if(total==0)return "aucune mesure";long rank=(total*95+99)/100,seen=0,p95=0;
            for(int i=0;i<BOUNDS.length;i++){seen+=buckets[i].sum();if(seen>=rank){p95=BOUNDS[i];break;}}
            return "n="+total+", moyenne="+(micros.sum()/total/1000D)+" ms, p95 "+(p95==Long.MAX_VALUE?">1000":"≤"+p95)+" ms, max="+max.get()+" ms";}
    }
    public static void api(long start){API.add(start);}public static void save(long start){SAVE.add(start);}
    public static void counterCache(long start) { COUNTER_CACHE.add(start); }
    public static void stockBatch(long start) { STOCK_BATCH.add(start); }
    public static String snapshot(){
        return "Réseau : " + API.text() + " | Sauvegarde vérifiée : " + SAVE.text()
                + " | Cache comptoir : " + COUNTER_CACHE.text() + " | Dépôt groupé : " + STOCK_BATCH.text();
    }
}
