import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Engine, listEngines } from '../api/engines';
import { useAuth } from '../auth/useAuth';

export default function EngineListPage() {
  const { user } = useAuth();
  const [engines, setEngines] = useState<Engine[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const isSysAdmin = user?.role === 'ROLE_SYS_ADMIN';

  useEffect(() => {
    listEngines()
      .then(setEngines)
      .catch((e) => setError((e as Error).message));
  }, []);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h1 style={{ margin: 0, fontSize: 20 }}>Engines</h1>
        {isSysAdmin && (
          <Link to="/admin/engines/new" className="button" data-testid="add-engine">
            Add engine
          </Link>
        )}
      </div>
      {error && <div className="error-banner">{error}</div>}
      {engines === null ? (
        <p>Loading…</p>
      ) : engines.length === 0 ? (
        <p>No engines yet.</p>
      ) : (
        <table data-testid="engines-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Host</th>
            </tr>
          </thead>
          <tbody>
            {engines.map((e) => (
              <tr key={e.id}>
                <td>{e.name}</td>
                <td>
                  {e.hostAlias} — {e.hostnameOrIp}:{e.port}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
