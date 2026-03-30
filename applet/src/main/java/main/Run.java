package main;

import applet.TestApplet;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import javax.smartcardio.*;

public class Run {
    public static void main(String[] args){
        // 1. create simulator
        CardSimulator simulator = new CardSimulator();

        // 2. install applet on simulator
        AID appletAID = AIDUtil.create("F000000001");
        simulator.installApplet(appletAID, TestApplet.class);

        // 3. select applet
        simulator.selectApplet(appletAID);

        // 4. send APDU
        CommandAPDU commandAPDU = new CommandAPDU(0x00, 0x60, 0x00, 0x00);
        ResponseAPDU response = simulator.transmitCommand(commandAPDU);

        System.out.println(new String(response.getData()));
    }

}