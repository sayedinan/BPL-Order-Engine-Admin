/**
 * 403 page. Used when a USER tries to reach /admin or any other
 * role-gated page. Link back to /dashboard.
 */
import { Link } from 'react-router-dom';

export function Forbidden() {
  return (
    <div className="error-page">
      <div className="error-page__card">
        <div className="error-page__code error-page__code--forbidden">403</div>
        <h1 className="error-page__title">Access denied</h1>
        <p className="error-page__message">
          Your role does not have permission to view this page.
        </p>
        <Link to="/dashboard" className="btn btn--primary">
          Back to Dashboard
        </Link>
      </div>
    </div>
  );
}
