package net.beaconcontroller.tutorial;

import java.util.Date;

public class PortTimestamp {
    private short port;
    private Date timeStamp;
    public short getPort() {
        return port;
    }
    public void setPort(short port) {
        this.port = port;
    }
    public Date getTimeStamp() {
        return timeStamp;
    }
    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }
    public PortTimestamp(short port, Date timeStamp) {
        super();
        this.port = port;
        this.timeStamp = timeStamp;
    }
    
}
