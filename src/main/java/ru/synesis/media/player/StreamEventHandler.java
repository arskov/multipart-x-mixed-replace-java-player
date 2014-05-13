package ru.synesis.media.player;

/**
 * 
 * @author Arseny Kovalchuk<br/><a href="http://www.linkedin.com/in/arsenykovalchuk/">LinkedIn&reg; Profile</a>
 *
 * @param <T>
 */
public interface StreamEventHandler<T extends StreamEvent> {
    
    public void handle(T event);
    
}
