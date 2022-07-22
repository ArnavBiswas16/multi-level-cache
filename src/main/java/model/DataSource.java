package model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class  DataSource<KEY, VALUE> {
    public CompletableFuture<VALUE> get(KEY key){
        return null;
    }

   public CompletableFuture<Void> persist(KEY key, VALUE value) {

        return null;
    }
}
