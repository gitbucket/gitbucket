package gitbucket.core.util;

import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.server.ServerBuilder;

import java.util.Arrays;
import java.util.Collections;

public class SshServerBuilder extends ServerBuilder {

    public SshServerBuilder(){
        keyExchangeFactories(
            NamedFactory.Utils.setUpTransformedFactories(true,
                 Collections.unmodifiableList(Arrays.asList(
                     BuiltinDHFactories.dhgex256,
                     BuiltinDHFactories.dhg14
                 )),
                ServerBuilder.DH2KEX
            )
        );
        cipherFactories(NamedFactory.Utils.setUpBuiltinFactories(true,
            Collections.unmodifiableList(Arrays.asList(
                BuiltinCiphers.aes192ctr,
                BuiltinCiphers.aes256ctr
            ))
        ));
        macFactories(NamedFactory.Utils.setUpBuiltinFactories(true,
             Collections.unmodifiableList(Arrays.asList(
                 BuiltinMacs.hmacsha1,
                 BuiltinMacs.hmacsha256,
                 BuiltinMacs.hmacsha512
            ))
        ));
    }

}