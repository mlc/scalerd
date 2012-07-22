package com.meetup.scalerd;

import com.google.common.base.Objects;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

public class RpcInput {
    private final String uri;
    private final String operations;

    @JsonCreator
    public RpcInput(@JsonProperty("uri") String uri,
                    @JsonProperty("operations") String operations) {
        this.uri = uri;
        this.operations = operations;
    }

    public String getUri() {
        return uri;
    }

    public String getOperations() {
        return operations;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        else if (obj == null)
            return false;
        else if (!(obj instanceof RpcInput))
            return false;

        RpcInput other = (RpcInput)obj;
        return Objects.equal(getUri(), other.getUri()) && Objects.equal(getOperations(), other.getOperations());
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("uri", uri).add("operations", operations).toString();
    }
}
