package model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;

import static model.PersistenceAlgorithm.WRITE_THROUGH;

public class cache<KEY, VALUE> {

    private static int capacity = 500;
    private int readTime;
    private int writeTime;

    private Map<KEY, Record> map = new HashMap<>();
    private DataSource<KEY, VALUE> dataSource;
    private final PersistenceAlgorithm persistenceAlgorithm;
    private final EvictionAlgorithm evictionAlgorithm;
    private final long expiryTimeInMilSeconds;
    private final Map<Long, List<Record<VALUE>>> expiryQueue;
    private final Map<AccessDetails, List<Record<VALUE>>> priorityQueue;


    public cache(int capacity, int readTime, int writeTime, DataSource<KEY, VALUE> dataSource, PersistenceAlgorithm persistenceAlgorithm, EvictionAlgorithm evictionAlgorithm, long expiryTimeInSeconds, long expiryTimeInMilSeconds) {
        this.capacity = capacity;
        this.readTime = readTime;
        this.writeTime = writeTime;
        this.evictionAlgorithm = evictionAlgorithm;
        this.expiryTimeInMilSeconds = expiryTimeInMilSeconds;
        this.map = new ConcurrentHashMap<>();
        this.dataSource = dataSource;
        this.persistenceAlgorithm = persistenceAlgorithm;
        this.expiryQueue = new ConcurrentSkipListMap<>();
        this.priorityQueue = new ConcurrentSkipListMap<>((a,b) ->  {
            int older = (int)(a.getAccessTimeStamp() - b.getAccessTimeStamp());
            if (evictionAlgorithm.equals(EvictionAlgorithm.LRU)){
                return older;
            } else {
                return a.getAccessCount() == b.getAccessCount() ? older : (int) (a.getAccessCount() - b.getAccessCount());
            }
        });
    }

    public Future<VALUE> get(KEY key){

        final CompletableFuture<VALUE> result;
        if (map.containsKey(key) && map.get(key).getAccessDetails().getAccessTimeStamp()< System.currentTimeMillis() - expiryTimeInMilSeconds){
            result = CompletableFuture.completedFuture(map.get(key).thenApply(Record::getValue));
        } else{
            result = dataSource.get(key).thenCompose(value -> addToCache(key, value).thenApply(__-value));
        }
        return result.thenAccept(record -> {
            expiryQueue.get(record.get).add(record);
            priorityQueue.get(key).add(record);


        });
    }

    public Future<Void> set(KEY key, VALUE value){

        Record<VALUE> valueRecord = new Record<>(value);
        if (!map.containsKey(key) && map.size() >= capacity){

          if (evictionAlgorithm.equals(EvictionAlgorithm.LRU)){

          } else {
              
          }

        }

        if (persistenceAlgorithm.equals(WRITE_THROUGH)){
            return dataSource.persist(key,value).thenAccept(__-> addToCache(key,value));

        } else {
            addToCache(key, value);
            dataSource.persist(key, value);
            return CompletableFuture.completedFuture(null);
        }
    }

    private Record<VALUE> addToCache(KEY key, VALUE value) {
        Record<VALUE> valueRecord = new Record<>(value);
        map.put(key, valueRecord);
        return map.get(key);
    }

}

class Record<VALUE> implements Comparable<Record<VALUE>>{
    private final VALUE value;
    private long loadTime;
    private final AccessDetails accessDetails;


    public Record(VALUE value) {
        this.value = value;
        this.accessDetails = new AccessDetails();
    }

    public VALUE getValue() {
        return value;
    }

    public AccessDetails getAccessDetails() {
        return accessDetails;
    }

    @Override
    public int compareTo(Record record) {
        return (int)(accessDetails.getAccessTimeStamp() - record.accessDetails.getAccessTimeStamp());
    }
}

class AccessDetails{
    private long accessTimeStamp;
    private long accessCount;

    public long getAccessTimeStamp() {
        return accessTimeStamp;
    }

    public void setAccessTimeStamp(long accessTimeStamp) {
        this.accessTimeStamp = accessTimeStamp;
    }

    public long getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(long accessCount) {
        this.accessCount = accessCount;
    }


}
