using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace Portal.Adapters.PortalStore;

public sealed class PortalStoreDesignTimeFactory : IDesignTimeDbContextFactory<PortalStoreDbContext>
{
    public PortalStoreDbContext CreateDbContext(string[] args) =>
        new(new DbContextOptionsBuilder<PortalStoreDbContext>()
            .UseSqlServer("Server=design-time;Database=portal;Integrated Security=false")
            .Options);
}
