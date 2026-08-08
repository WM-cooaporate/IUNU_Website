import { useState } from "react";
import { Link } from "react-router-dom";

function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const handleSubmit = (event) => {
    event.preventDefault();

    setError("");
    setMessage("");

    if (!email) {
      setError("Please enter your email.");
      return;
    }

    /*
      Later:
      authServices.forgotPassword(email)
    */

    console.log(
      "Forgot password email:",
      email
    );

    setMessage(
      "If this email exists, a reset link will be sent."
    );
  };

  return (
    <div className="forgot-page">

      <div className="forgot-box">

        <h1>Forgot Password</h1>

        <p>
          Enter your email to reset your password.
        </p>

        <form onSubmit={handleSubmit}>

          <div>
            <label htmlFor="email">
              Email
            </label>

            <input
              id="email"
              type="email"
              placeholder="Enter your email"
              value={email}
              onChange={(event) =>
                setEmail(event.target.value)
              }
            />
          </div>

          {error && (
            <p className="auth-error">
              {error}
            </p>
          )}

          {message && (
            <p className="auth-success">
              {message}
            </p>
          )}

          <button type="submit">
            Send Reset Link
          </button>

        </form>

        <p>
          <Link to="/login">
            Back to Login
          </Link>
        </p>

      </div>

    </div>
  );
}

export default ForgotPassword;