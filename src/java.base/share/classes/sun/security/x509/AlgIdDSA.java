/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.x509;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.DSAParams;
import java.util.Arrays;
import sun.security.util.*;


/**
 * This class identifies DSS/DSA Algorithm variants, which are distinguished
 * by using different algorithm parameters <em>P, Q, G</em>.  It uses the
 * NIST/IETF standard DER encoding.  These are used to implement the Digital
 * Signature Standard (DSS), FIPS 186.
 *
 * <P><em><b>NOTE:</b></em>  DSS/DSA Algorithm IDs may be created without these
 * parameters.  Use of DSS/DSA in modes where parameters are
 * either implicit (e.g. a default applicable to a site or a larger scope),
 * or are derived from some Certificate Authority's DSS certificate, is
 * not supported directly.  The application is responsible for creating a key
 * containing the required parameters prior to using the key in cryptographic
 * operations.  The follwoing is an example of how this may be done assuming
 * that we have a certificate called <code>currentCert</code> which doesn't
 * contain DSS/DSA parameters and we need to derive DSS/DSA parameters
 * from a CA's certificate called <code>caCert</code>.
 *
 * <pre>{@code
 * // key containing parameters to use
 * DSAPublicKey cAKey = (DSAPublicKey)(caCert.getPublicKey());
 * // key without parameters
 * DSAPublicKey nullParamsKey = (DSAPublicKey)(currentCert.getPublicKey());
 *
 * DSAParams cAKeyParams = cAKey.getParams();
 * KeyFactory kf = KeyFactory.getInstance("DSA");
 * DSAPublicKeySpec ks = new DSAPublicKeySpec(nullParamsKey.getY(),
 *                                            cAKeyParams.getP(),
 *                                            cAKeyParams.getQ(),
 *                                            cAKeyParams.getG());
 * DSAPublicKey usableKey = kf.generatePublic(ks);
 * }</pre>
 *
 * @see java.security.interfaces.DSAParams
 * @see java.security.interfaces.DSAPublicKey
 * @see java.security.KeyFactory
 * @see java.security.spec.DSAPublicKeySpec
 *
 * @author David Brownell
 */
public final class AlgIdDSA extends AlgorithmId implements DSAParams {

    @java.io.Serial
    private static final long serialVersionUID = 3437177836797504046L;

    private static class DSAComponents {
        private final BigInteger p;
        private final BigInteger q;
        private final BigInteger g;
        DSAComponents(BigInteger p, BigInteger q, BigInteger g) {
            this.p = p;
            this.q = q;
            this.g = g;
        }
    }

    /*
     * The three unsigned integer parameters.
     */
    private BigInteger p, q, g;

    /** Returns the DSS/DSA parameter "P" */
    public BigInteger   getP() { return p; }

    /** Returns the DSS/DSA parameter "Q" */
    public BigInteger   getQ() { return q; }

    /** Returns the DSS/DSA parameter "G" */
    public BigInteger   getG() { return g; }

    /**
     * Default constructor.  The OID and parameters must be
     * deserialized before this algorithm ID is used.
     */
    @Deprecated
    public AlgIdDSA() {}

    /**
     * Constructs a DSS/DSA Algorithm ID from numeric parameters.
     * If all three are null, then the parameters portion of the algorithm id
     * is set to null.  See note in header regarding use.
     *
     * @param p the DSS/DSA parameter "P"
     * @param q the DSS/DSA parameter "Q"
     * @param g the DSS/DSA parameter "G"
     */
    public AlgIdDSA(BigInteger p, BigInteger q, BigInteger g) {
        super (DSA_oid);

        if (p != null || q != null || g != null) {
            if (p == null || q == null || g == null)
                throw new ProviderException("Invalid parameters for DSS/DSA" +
                                            " Algorithm ID");
            try {
                this.p = p;
                this.q = q;
                this.g = g;
                // For algorithm IDs which haven't been created from a DER
                // encoded value, need to create DER encoding and store it
                // into "encodedParams"
                encodedParams = encode(p, q, g);
            } catch (IOException e) {
                /* this should not happen */
                throw new ProviderException ("Construct DSS/DSA Algorithm ID");
            }
        }
    }

    /**
     * Returns "DSA", indicating the Digital Signature Algorithm (DSA) as
     * defined by the Digital Signature Standard (DSS), FIPS 186.
     */
    public String getName() {
        return "DSA";
    }

    /*
     * Returns a formatted string describing the parameters.
     */
    public String toString () {
        return paramsToString();
    }

    /*
     * Returns a string describing the parameters.
     */
    protected String paramsToString () {
        if (encodedParams == null) {
            return " null\n";
        } else {
            return "\n    p:\n" + Debug.toHexString(p) +
                    "\n    q:\n" + Debug.toHexString(q) +
                    "\n    g:\n" + Debug.toHexString(g) +
                    "\n";
        }
    }

    /**
     * Restores the state of this object from the stream. Override to check
     * on the 'p', 'q', 'g', and 'encodedParams'.
     *
     * @param  stream the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(ObjectInputStream stream) throws IOException {
        try {
            stream.defaultReadObject();
            // if any of the 'p', 'q', 'g', 'encodedParams' is non-null,
            // then they must be all non-null w/ matching encoding
            if ((p != null || q != null || g != null || encodedParams != null)
                    && !Arrays.equals(encodedParams, encode(p, q, g))) {
                throw new InvalidObjectException("Invalid DSA alg params");
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    /*
     * Create the DER encoding w/ the specified 'p', 'q', 'g'
     */
    private static byte[] encode(BigInteger p, BigInteger q,
            BigInteger g) throws IOException {
        if (p == null || q == null || g == null) {
            throw new InvalidObjectException("invalid null value");
        }
        DerOutputStream out = new DerOutputStream();
        out.putInteger(p);
        out.putInteger(q);
        out.putInteger(g);
        DerOutputStream result = new DerOutputStream();
        result.write(DerValue.tag_Sequence, out);
        return result.toByteArray();
    }
}
