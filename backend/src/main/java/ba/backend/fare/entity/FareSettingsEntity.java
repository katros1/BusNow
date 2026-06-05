package ba.backend.fare.entity;

import ba.backend.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "fare_settings")
public class FareSettingsEntity extends BaseEntity {

    @Column(name = "base_price_frw", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePriceFrw;

    protected FareSettingsEntity() {}

    public FareSettingsEntity(BigDecimal basePriceFrw) {
        this.basePriceFrw = basePriceFrw;
    }

    public BigDecimal getBasePriceFrw() {
        return basePriceFrw;
    }

    public void setBasePriceFrw(BigDecimal basePriceFrw) {
        this.basePriceFrw = basePriceFrw;
    }
}
