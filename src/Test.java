import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Test {

    public static void main(String[] args) {
        Map map=new ConcurrentHashMap();
        map.put("1","wp");
        System.out.println(map);
    }
}
