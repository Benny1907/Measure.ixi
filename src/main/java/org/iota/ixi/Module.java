package org.iota.ixi;


import org.iota.ict.eee.EffectListenerQueue;
import org.iota.ict.eee.Environment;
import org.iota.ict.ixi.Ixi;
import org.iota.ict.ixi.IxiModule;
import org.iota.ict.ixi.context.IxiContext;
import org.iota.ict.model.transaction.Transaction;
import org.iota.ict.model.transaction.TransactionBuilder;
import org.iota.ict.network.gossip.GossipEvent;
import org.iota.ict.network.gossip.GossipListener;

import org.iota.ixi.configuration.Migrator;
import view.MeasureIxiContext;

import java.util.logging.Logger;

/**
 * This is an example IXI module. Use it as template to implement your own module. Run Main.main() to test it.
 * Do not move this class into a different package. If you rename it, update your module.json,
 * Use this constructor only for initialization because it blocks Ict. Instead, use run() as main thread.
 * https://github.com/iotaledger/ixi
 * */
public class Module extends IxiModule {

    private final MeasureIxiContext context = new MeasureIxiContext();
    private static final Logger log = Logger.getLogger("MeasureIxi");


    public Module(Ixi ixi) {
        super(ixi);
        ixi.addListener(new EffectListenerQueue(new CustomGossipListener().getEnvironment()));
    }

    @Override
    public void install() {
        // Attempt config migration from older Report.ixi versions if config is not found for the current version.
        Migrator.migrateIfConfigurationMissing();
    }

    @Override
    public void uninstall() {

    }

    @Override
    public void onStart() {
        log.info("Starting ReportIxi ...");


    }
    public void run() {
        // submit a new transaction
        TransactionBuilder builder = new TransactionBuilder();
        builder.asciiMessage("Hello World!");
        Transaction toSubmit = builder.build();
        context.getConfiguration();

        try {
            while (true) {

                ixi.submit(toSubmit);
                System.out.println("Sende");

                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            if (isRunning()) {
                e.printStackTrace();

                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public IxiContext getContext() {
        return context;
    }

    public MeasureIxiContext getMeasureIxiContext() {
        return context;
    }
}

/**
 * A custom gossip listener which prints every message submitted or received.
 * */
class CustomGossipListener implements GossipListener {


    @Override
    public void onReceive(GossipEvent effect) {
        System.out.println("Received tx hash : " + effect.getTransaction().hash);
    }

    @Override
    public Environment getEnvironment() {
        return new Environment("MyModule.env");
    }
}