package ru.synesis.media.player;

/**
 * 
 * @author Arseny Kovalchuk<br/><a href="http://www.linkedin.com/in/arsenykovalchuk/">LinkedIn&reg; Profile</a>
 *
 */
public interface StreamEvent {
    
    String getMessage();
    
    StreamThread getStreamThread();

}
