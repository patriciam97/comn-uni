import java.util.Calendar;
import java.util.GregorianCalendar;

public class Timer {
    int timeEllapsed;
    int startMS; //miliseconds
    int currentMS;
    int timeoutMS;

    public StartTime(int timeoutInMseconds) {
        // work out current time in seconds and
        Calendar cal = new GregorianCalendar();
        int sec = cal.get(Calendar.SECOND);
        int min = cal.get(Calendar.MINUTE);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int milliSec = cal.get(Calendar.MILLISECOND);
        this.startMS = milliSec + (sec*1000) + (min *60000) + (hour*3600000);
        this.timeoutMS = (timeoutInMseconds);
    }

    public void getTimeElapsed() {
        Calendar cal = new GregorianCalendar();
        int secElapsed = cal.get(Calendar.SECOND);
        int minElapsed = cal.get(Calendar.MINUTE);
        int hourElapsed = cal.get(Calendar.HOUR_OF_DAY);
        int milliSecElapsed = cal.get(Calendar.MILLISECOND);
        this.currentMS = milliSecElapsed + (secElapsed*1000) + (minElapsed *60000) + (hourElapsed * 3600000);
        THIS.timeEllapsed = this.currentMS - this.startMS;
    }

    public boolean timeout() {
        getTimeElapsed();
        if (this.timeEllapsed >= this.timeoutMS) {
            return true;
        } else {
            return false;
        }
    }
}
