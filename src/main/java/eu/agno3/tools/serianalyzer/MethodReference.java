/**
 * © 2015 AgNO3 Gmbh & Co. KG
 * All right reserved.
 * 
 * Created: 11.11.2015 by mbechler
 */
package eu.agno3.tools.serianalyzer;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.jboss.jandex.DotName;
import org.objectweb.asm.Type;


/**
 * @author mbechler
 *
 */
public class MethodReference implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -6109397570552531402L;

    private String typeName;
    private String method;
    private String signature;
    private boolean intf;
    private boolean stat;

    private boolean anyTaint;
    private BitSet parameterTaint = new BitSet();
    private BitSet parameterReturnTaint = new BitSet();
    private boolean calleeTaint;

    private transient List<Type> argumentTypes;
    private transient Type targetType;

    private int argsLength;

    private transient Integer cachedHashCode;
    private transient DotName nameCache;


    /**
     * 
     */
    MethodReference () {}


    /**
     * @param typeName
     * @param intf
     * @param method
     * @param stat
     * @param signature
     */
    public MethodReference ( String typeName, boolean intf, String method, boolean stat, String signature ) {
        super();
        this.typeName = typeName.intern();
        this.intf = intf;
        this.method = method.intern();
        this.stat = stat;
        this.signature = signature.intern();
        this.argsLength = Type.getArgumentTypes(signature).length;
    }


    private void writeObject ( ObjectOutputStream oos ) throws IOException {
        oos.defaultWriteObject();

        oos.writeBoolean(this.targetType != null);
        if ( this.targetType != null ) {
            oos.writeUTF(this.targetType.toString());
        }

        if ( this.argumentTypes != null ) {
            oos.writeBoolean(true);
            oos.writeInt(this.argumentTypes != null ? this.argumentTypes.size() : 0);
            for ( Type t : this.argumentTypes ) {
                oos.writeUTF(t != null ? t.toString() : null);
            }
        }
        else {
            oos.writeBoolean(false);
        }
    }


    private void readObject ( ObjectInputStream ois ) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();

        if ( ois.readBoolean() ) {
            this.targetType = Type.getType(ois.readUTF());
        }

        String tname;
        boolean haveArgs = ois.readBoolean();
        if ( haveArgs ) {
            int argc = ois.readInt();
            List<Type> argTypes = new ArrayList<>();
            for ( int i = 0; i < argc; i++ ) {
                tname = ois.readUTF();
                argTypes.add(Type.getType(tname));
            }
            this.argumentTypes = argTypes;
        }
    }


    /**
     * {@inheritDoc}
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString () {
        StringBuilder taintStatus = new StringBuilder();
        for ( int i = 0; i < this.argsLength; i++ ) {
            taintStatus.append(this.parameterTaint.get(i) ? 'T' : 'U');
        }
        char callerTaint = this.calleeTaint ? 'T' : 'U';

        StringBuilder argTypeString = new StringBuilder();
        if ( this.argumentTypes != null ) {
            argTypeString.append('/');
            argTypeString.append('(');
            for ( Type type : this.argumentTypes ) {
                argTypeString.append(type);
                argTypeString.append(',');
            }
            argTypeString.append(')');
        }
        return String.format("%s%s%s [%s]%s %s[%s] %s", //$NON-NLS-1$
            this.typeName,
            this.stat ? "::" : "->", this.method, this.signature, argTypeString, callerTaint, taintStatus, this.intf ? " interface" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    }


    /**
     * @return the called target type
     */
    public Type getTargetType () {
        return this.targetType;
    }


    /**
     * @param targetType
     *            the targetType to set
     */
    public void setTargetType ( Type targetType ) {
        this.cachedHashCode = null;
        if ( targetType == null || targetType.getClassName().equals(this.typeName.toString()) ) {
            this.targetType = null;
            return;
        }
        this.targetType = targetType;
    }


    /**
     * @return the argumentTypes
     */
    public List<Type> getArgumentTypes () {
        return this.argumentTypes;
    }


    /**
     * @param argumentTypes
     *            the argumentTypes to set
     */
    public void setArgumentTypes ( List<Type> argumentTypes ) {
        this.cachedHashCode = null;
        if ( argumentTypes == null || argumentTypes.isEmpty() ) {
            this.argumentTypes = null;
            return;
        }
        Type[] sigTypes = Type.getArgumentTypes(this.signature);
        if ( Arrays.equals(sigTypes, argumentTypes.toArray()) ) {
            return;
        }
        this.argumentTypes = argumentTypes;
    }


    /**
     * @return whether the target type is a interface
     */
    public boolean isInterface () {
        return this.intf;
    }


    /**
     * @return the stat
     */
    public boolean isStatic () {
        return this.stat;
    }


    /**
     * @return the type name as a string
     */
    public String getTypeNameString () {
        return this.typeName;
    }


    /**
     * @return the typeName
     */
    public DotName getTypeName () {
        if ( this.nameCache == null ) {
            this.nameCache = DotName.createSimple(this.typeName);
        }
        return this.nameCache;
    }


    /**
     * @return the method
     */
    public String getMethod () {
        return this.method;
    }


    /**
     * @return the signature
     */
    public String getSignature () {
        return this.signature;
    }


    /**
     * @param i
     * @return whether the parameter is tainted
     */
    public boolean isParameterTainted ( int i ) {
        return this.parameterTaint.get(i);
    }


    /**
     * @param i
     * @return taint status changed
     */
    public boolean taintParameter ( int i ) {
        this.anyTaint = true;
        boolean old = isParameterTainted(i);
        this.parameterTaint.set(i);
        return !old;
    }


    /**
     * 
     * @param i
     * @return wheteher all return values from the argument should be tainted
     */
    public boolean isTaintParameterReturn ( int i ) {
        return this.parameterReturnTaint.get(i);
    }


    /**
     * 
     * @param i
     * @return taint status changed
     */
    public boolean taintParameterReturns ( int i ) {
        this.anyTaint = true;
        boolean old = isTaintParameterReturn(i);
        this.parameterReturnTaint.set(i);
        return !old;
    }


    /**
     * @return whether the callee is tainted
     */
    public boolean isCalleeTainted () {
        return this.calleeTaint;
    }


    /**
     * 
     * @return whether taint status changed
     */
    public boolean taintCallee () {
        this.cachedHashCode = null;
        this.anyTaint = true;
        boolean old = this.calleeTaint;
        this.calleeTaint = true;
        return !old;
    }


    /**
     * {@inheritDoc}
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode () {

        if ( this.cachedHashCode != null ) {
            return this.cachedHashCode;
        }

        final int prime = 31;
        int result = 1;
        result = prime * result + ( this.calleeTaint ? 1231 : 1237 );
        result = prime * result + ( this.intf ? 1231 : 1237 );
        result = prime * result + ( ( this.method == null ) ? 0 : this.method.hashCode() );
        result = prime * result + ( ( this.signature == null ) ? 0 : this.signature.hashCode() );
        result = prime * result + ( this.stat ? 1231 : 1237 );
        result = prime * result + ( ( this.typeName == null ) ? 0 : this.typeName.hashCode() );
        this.cachedHashCode = result;
        return result;
    }


    /**
     * {@inheritDoc}
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals ( Object obj ) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        MethodReference other = (MethodReference) obj;

        if ( other.hashCode() != this.hashCode() ) {
            return false;
        }

        if ( other.anyTaint ^ this.anyTaint ) {
            return false;
        }

        if ( this.intf != other.intf )
            return false;
        if ( this.calleeTaint != other.calleeTaint )
            return false;

        if ( this.method == null ) {
            if ( other.method != null )
                return false;
        }
        else if ( !this.method.equals(other.method) )
            return false;
        if ( this.stat != other.stat )
            return false;

        if ( this.parameterReturnTaint == null ) {
            if ( other.parameterReturnTaint != null )
                return false;
        }
        else if ( !this.parameterReturnTaint.equals(other.parameterReturnTaint) )
            return false;
        if ( this.parameterTaint == null ) {
            if ( other.parameterTaint != null )
                return false;
        }
        else if ( !this.parameterTaint.equals(other.parameterTaint) )
            return false;

        if ( this.typeName == null ) {
            if ( other.typeName != null )
                return false;
        }
        else if ( !this.typeName.toString().equals(other.typeName.toString()) )
            return false;

        if ( this.signature == null ) {
            if ( other.signature != null )
                return false;
        }
        else if ( !this.signature.equals(other.signature) )
            return false;

        if ( this.targetType == null ) {
            if ( other.targetType != null )
                return false;
        }
        else if ( !this.targetType.equals(other.targetType) )
            return false;

        if ( this.argumentTypes == null ) {
            if ( other.argumentTypes != null )
                return false;
        }
        else if ( !this.argumentTypes.equals(other.argumentTypes) )
            return false;

        return true;
    }


    /**
     * @param name
     * @return a new method reference for the given target type
     */
    public MethodReference adaptToType ( DotName name ) {
        MethodReference ref = new MethodReference(name.toString(), false, this.getMethod(), this.stat, this.getSignature());
        if ( this.calleeTaint ) {
            ref.taintCallee();
        }
        for ( int i = 0; i < this.argsLength; i++ ) {
            if ( this.isParameterTainted(i) ) {
                ref.taintParameter(i);
            }
            if ( this.isTaintParameterReturn(i) ) {
                ref.taintParameterReturns(i);
            }
        }

        if ( this.argumentTypes != null ) {
            ref.argumentTypes = new ArrayList<>(this.argumentTypes);
        }
        return ref;
    }


    /**
     * @return a new method that reflects full taint status
     */
    public MethodReference fullTaint () {
        MethodReference ref = new MethodReference(this.typeName, this.isInterface(), this.getMethod(), this.isStatic(), this.getSignature());
        ref.taintCallee();
        for ( int i = 0; i < this.argsLength; i++ ) {
            ref.taintParameter(i);
        }
        return ref;
    }


    /**
     * @param other
     * @return a new method that reflects the union of the two methods taint states
     */
    public MethodReference maxTaint ( MethodReference other ) {
        MethodReference aComp = this.comparable();
        MethodReference bComp = other.comparable();
        if ( !aComp.equals(bComp) ) {
            throw new IllegalArgumentException("Not same method"); //$NON-NLS-1$
        }
        MethodReference ref = new MethodReference(this.typeName, this.isInterface(), this.getMethod(), this.isStatic(), this.getSignature());
        if ( this.calleeTaint || other.calleeTaint ) {
            ref.taintCallee();
        }
        for ( int i = 0; i < this.argsLength; i++ ) {
            if ( this.isParameterTainted(i) || other.isParameterTainted(i) ) {
                ref.taintParameter(i);
            }
            if ( this.isTaintParameterReturn(i) || other.isTaintParameterReturn(i) ) {
                ref.taintParameterReturns(i);
            }
        }
        return ref;
    }


    /**
     * @return a compareable type name without tainting information
     */
    public MethodReference comparable () {
        if ( !this.anyTaint && this.targetType == null && this.argumentTypes == null ) {
            return this;
        }
        return new MethodReference(this.typeName, this.isInterface(), this.getMethod(), this.isStatic(), this.getSignature());
    }


    /**
     * @param other
     * @return whether this reference implies the other one
     */
    public boolean implies ( MethodReference other ) {
        if ( ( other.anyTaint && !this.anyTaint ) || ( other.calleeTaint && !this.calleeTaint ) ) {
            return false;
        }
        if ( this.intf != other.intf )
            return false;
        if ( this.calleeTaint != other.calleeTaint )
            return false;

        if ( this.typeName == null ) {
            if ( other.typeName != null )
                return false;
        }
        else if ( !this.typeName.toString().equals(other.typeName.toString()) )
            return false;

        if ( this.signature == null ) {
            if ( other.signature != null )
                return false;
        }
        else if ( !this.signature.equals(other.signature) )
            return false;

        if ( this.argumentTypes == null ) {
            if ( other.argumentTypes != null )
                return false;
        }
        else if ( !this.argumentTypes.equals(other.argumentTypes) )
            return false;

        if ( this.targetType == null ) {
            if ( other.targetType != null )
                return false;
        }
        else if ( !this.targetType.equals(other.targetType) )
            return false;

        for ( int i = 0; i < this.argsLength; i++ ) {
            if ( other.isParameterTainted(i) && !this.isParameterTainted(i) ) {
                return false;
            }
            if ( other.isTaintParameterReturn(i) && !this.isTaintParameterReturn(i) ) {
                return false;
            }
        }

        return true;
    }

}
