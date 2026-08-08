import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

function Login() {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    email: "",
    password: "",
  });

  const [error, setError] = useState("");

  const handleChange = (event) => {
    const { name, value } = event.target;

    setFormData((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  const handleSubmit = (event) => {
    event.preventDefault();

    setError("");

    if (!formData.email || !formData.password) {
      setError("Please enter your email and password.");
      return;
    }

    /*
      Temporary mock login.

      Later this will be replaced with:
      authServices.login(formData)
    */

    console.log("Login data:", formData);

    localStorage.setItem(
      "isLoggedIn",
      "true"
    );

    localStorage.setItem(
      "userEmail",
      formData.email
    );

    navigate("/");
  };

  return (
    <div className="login-page">

      <div className="login-box">

        <h1>Welcome Back</h1>

        <p>Login to your account</p>

        <form onSubmit={handleSubmit}>

          <div>
            <label htmlFor="email">
              Email
            </label>

            <input
              id="email"
              name="email"
              type="email"
              placeholder="Enter your email"
              value={formData.email}
              onChange={handleChange}
            />
          </div>

          <div>
            <label htmlFor="password">
              Password
            </label>

            <input
              id="password"
              name="password"
              type="password"
              placeholder="Enter your password"
              value={formData.password}
              onChange={handleChange}
            />
          </div>

          {error && (
            <p className="auth-error">
              {error}
            </p>
          )}

          <button type="submit">
            Login
          </button>

        </form>

        <p>
          <Link to="/forgot-password">
            Forgot Password?
          </Link>
        </p>

        <p>
          Don't have an account?{" "}

          <Link to="/register">
            Register
          </Link>
        </p>

      </div>

    </div>
  );
}

export default Login;