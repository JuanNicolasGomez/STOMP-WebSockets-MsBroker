package edu.eci.arsw.collabpaint.persistence;

import edu.eci.arsw.collabpaint.model.Point;
import edu.eci.arsw.collabpaint.util.JedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RedisPersistenceService implements PersistenceService {

    @Autowired
    private SimpMessagingTemplate msgt;


    private CopyOnWriteArrayList<Point> points = new CopyOnWriteArrayList<>();

    private String luaScript = "local xval,yval; \n" +
            "if (redis.call('LLEN','x')==4) then \n" +
            "\txval=redis.call('LRANGE','x',0,-1);\n" +
            "\tyval=redis.call('LRANGE','y',0,-1);\n" +
            "\tredis.call('DEL','x'); \n" +
            "\tredis.call('DEL','y'); \t\t\n" +
            "\treturn {xval,yval}; \n" +
            "else \n" +
            "\treturn {}; \n" +
            "end";

    @Override
    public void handleNewPoint(Point pt, String numDibujo) {
        //System.out.println("Using Redis handler");

        Jedis jedis = JedisUtil.getPool().getResource();
        jedis.watch("x", "y");
        Transaction t = jedis.multi();
        //t.set("points", "x);
        //System.out.println("SIZEEEEEE:   -  " + t.exec().size());
        t.rpush("x", String.valueOf(pt.getX()));
        t.rpush("y", String.valueOf(pt.getY()));
        Response<Object> luares = t.eval(luaScript.getBytes(), 0,"0".getBytes());
        List<Object> res=t.exec();
        if (res.size() >0) {
            System.out.println("Nuevo punto recibido en el servidor! (Peristencia usando REDIS) :" + pt);
            if (((ArrayList) luares.get()).size() == 2) {
                System.out.println("First Point X Value: " + new String((byte[]) ((ArrayList) (((ArrayList) luares.get()).get(0))).get(0)));
                ArrayList<Object> coordX = (ArrayList)(((ArrayList) luares.get()).get(0));
                ArrayList<Object> coordY = (ArrayList)(((ArrayList) luares.get()).get(1));
                for(int i = 0; i <4; i++){
                    Point p = new Point(Integer.parseInt(new String((byte[]) coordX.get(i))), Integer.parseInt(new String((byte[]) coordY.get(i))));
                    points.add(p);
                }

                msgt.convertAndSend("/topic/newpolygon." + numDibujo, points);
                points.clear();
            }
            msgt.convertAndSend("/topic/newpoint." + numDibujo, pt);
        }else{
            System.out.println("Error al recibir punto.");
        }
        jedis.close();


    }
}
